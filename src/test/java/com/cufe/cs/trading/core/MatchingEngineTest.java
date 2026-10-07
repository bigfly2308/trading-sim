package com.cufe.cs.trading.core;

import com.cufe.cs.trading.model.OrderType;
import com.cufe.cs.trading.model.Side;
import com.cufe.cs.trading.model.Trade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchingEngineTest {

    private AccountManager accounts;
    private MatchingEngine engine;
    private MarketData marketData;

    @BeforeEach
    void setUp() {
        accounts = new AccountManager();
        accounts.register(new Account("buyer", 100000));
        accounts.register(new Account("seller", 0, Map.of("AAPL", 100)));
        marketData = new MarketData(Map.of("AAPL", 175.50));
        engine = new MatchingEngine(accounts, marketData);
    }

    @Test
    void limitBuyPostsWhenNoMatch() {
        List<Trade> trades = engine.submitOrder("AAPL", Side.BUY, OrderType.LIMIT, 170.0, 10, "buyer");
        assertTrue(trades.isEmpty());
        assertEquals(1, engine.getBook("AAPL").bidCount());
    }

    @Test
    void limitBuyMatchesRestingAskAtAskPrice() {
        engine.submitOrder("AAPL", Side.SELL, OrderType.LIMIT, 174.50, 50, "seller");
        List<Trade> trades = engine.submitOrder("AAPL", Side.BUY, OrderType.LIMIT, 175.00, 100, "buyer");
        assertEquals(1, trades.size());
        assertEquals(174.50, trades.get(0).getPrice(), 0.0001);
        assertEquals(50, trades.get(0).getQuantity());
        // 买方剩余 50 股挂入订单簿
        assertEquals(1, engine.getBook("AAPL").bidCount());
        assertEquals(50, accounts.require("buyer").getHolding("AAPL"));
        assertEquals(8725.0, accounts.require("seller").getCash(), 0.01);
    }

    @Test
    void priceTimePriority() {
        accounts.register(new Account("s2", 0, Map.of("AAPL", 100)));
        engine.submitOrder("AAPL", Side.SELL, OrderType.LIMIT, 174.50, 30, "seller");
        engine.submitOrder("AAPL", Side.SELL, OrderType.LIMIT, 174.50, 70, "s2");
        List<Trade> trades = engine.submitOrder("AAPL", Side.BUY, OrderType.LIMIT, 174.50, 50, "buyer");
        assertEquals(2, trades.size());
        assertEquals("seller", trades.get(0).getSellAgentId());
        assertEquals(30, trades.get(0).getQuantity());
        assertEquals("s2", trades.get(1).getSellAgentId());
        assertEquals(20, trades.get(1).getQuantity());
    }

    @Test
    void marketBuyConsumesAsks() {
        engine.submitOrder("AAPL", Side.SELL, OrderType.LIMIT, 174.50, 50, "seller");
        engine.submitOrder("AAPL", Side.SELL, OrderType.LIMIT, 175.00, 50, "seller");
        List<Trade> trades = engine.submitOrder("AAPL", Side.BUY, OrderType.MARKET, Double.NaN, 60, "buyer");
        assertEquals(2, trades.size());
        assertEquals(174.50, trades.get(0).getPrice(), 0.0001);
        assertEquals(50, trades.get(0).getQuantity());
        assertEquals(175.00, trades.get(1).getPrice(), 0.0001);
        assertEquals(10, trades.get(1).getQuantity());
    }

    @Test
    void marketSellConsumesBids() {
        accounts.register(new Account("buyer2", 100000));
        engine.submitOrder("AAPL", Side.BUY, OrderType.LIMIT, 175.00, 40, "buyer2");
        List<Trade> trades = engine.submitOrder("AAPL", Side.SELL, OrderType.MARKET, Double.NaN, 40, "seller");
        assertEquals(1, trades.size());
        assertEquals(175.00, trades.get(0).getPrice(), 0.0001);
        assertEquals(40, trades.get(0).getQuantity());
        assertEquals(7000.0, accounts.require("seller").getCash(), 0.01);
    }

    @Test
    void cancelOrderReleasesReservation() {
        engine.submitOrder("AAPL", Side.SELL, OrderType.LIMIT, 174.50, 50, "seller");
        assertEquals(50, accounts.require("seller").getHolding("AAPL"));
        List<String> ids = engine.activeOrderIds("seller");
        assertEquals(1, ids.size());
        assertTrue(engine.cancelOrder(ids.get(0), "seller"));
        assertEquals(100, accounts.require("seller").getHolding("AAPL"));
        assertEquals(0, engine.activeOrderCount());
    }

    @Test
    void rejectsInsufficientFunds() {
        assertThrows(InsufficientFundsException.class,
                () -> engine.submitOrder("AAPL", Side.BUY, OrderType.LIMIT, 99999.0, 100, "buyer"));
    }

    @Test
    void rejectsInsufficientShares() {
        assertThrows(InsufficientSharesException.class,
                () -> engine.submitOrder("AAPL", Side.SELL, OrderType.LIMIT, 175.0, 999, "seller"));
    }

    @Test
    void rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> engine.submitOrder("AAPL", Side.BUY, OrderType.LIMIT, 0.0, 10, "buyer"));
        assertThrows(IllegalArgumentException.class,
                () -> engine.submitOrder("AAPL", Side.BUY, OrderType.LIMIT, 175.0, 0, "buyer"));
        assertThrows(IllegalArgumentException.class,
                () -> engine.submitOrder("AAPL", Side.BUY, OrderType.LIMIT, 175.0, 10, "nobody"));
    }

    @Test
    void multiSymbolIsolation() {
        marketData = new MarketData(Map.of("AAPL", 175.5, "GOOGL", 141.8));
        engine = new MatchingEngine(accounts, marketData);
        engine.submitOrder("AAPL", Side.SELL, OrderType.LIMIT, 174.5, 10, "seller");
        List<Trade> trades = engine.submitOrder("GOOGL", Side.BUY, OrderType.LIMIT, 200.0, 10, "buyer");
        assertTrue(trades.isEmpty());
        assertNotNull(engine.getBook("AAPL"));
        assertNotNull(engine.getBook("GOOGL"));
        assertEquals(1, engine.getBook("GOOGL").bidCount());
    }

    @Test
    void concurrentOrdersDoNotLoseUpdates() throws Exception {
        accounts.register(new Account("bigSeller", 0, Map.of("AAPL", 1000)));
        engine.submitOrder("AAPL", Side.SELL, OrderType.LIMIT, 174.50, 1000, "bigSeller");
        for (int i = 0; i < 10; i++) {
            accounts.register(new Account("b" + i, 100000));
        }

        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Callable<List<Trade>>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final int idx = i;
            tasks.add(() -> engine.submitOrder("AAPL", Side.BUY, OrderType.MARKET, Double.NaN, 100, "b" + idx));
        }
        List<Future<List<Trade>>> futures = pool.invokeAll(tasks);
        pool.shutdown();

        int totalFilled = 0;
        for (Future<List<Trade>> future : futures) {
            for (Trade trade : future.get()) {
                totalFilled += trade.getQuantity();
            }
        }
        assertEquals(1000, totalFilled);
        assertEquals(174500.0, accounts.require("bigSeller").getCash(), 0.01);
        int totalShares = 0;
        for (int i = 0; i < 10; i++) {
            totalShares += accounts.require("b" + i).getHolding("AAPL");
        }
        assertEquals(1000, totalShares);
        assertEquals(0, engine.getBook("AAPL").askCount());
    }
}
