package com.cufe.cs.trading.core;

import com.cufe.cs.trading.model.Order;
import com.cufe.cs.trading.model.OrderType;
import com.cufe.cs.trading.model.PriceLevel;
import com.cufe.cs.trading.model.Side;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderBookTest {

    private Order buy(double price, long id) {
        return new Order(id, "AAPL", Side.BUY, OrderType.LIMIT, price, 100, "a");
    }

    private Order sell(double price, long id) {
        return new Order(id, "AAPL", Side.SELL, OrderType.LIMIT, price, 100, "a");
    }

    @Test
    void bidsOrderedByPriceDescending() {
        OrderBook book = new OrderBook("AAPL");
        book.add(buy(150.0, 1));
        book.add(buy(155.0, 2));
        book.add(buy(152.0, 3));
        assertEquals(155.0, book.bestBid().getPrice(), 0.0001);
    }

    @Test
    void asksOrderedByPriceAscending() {
        OrderBook book = new OrderBook("AAPL");
        book.add(sell(152.0, 1));
        book.add(sell(150.0, 2));
        book.add(sell(155.0, 3));
        assertEquals(150.0, book.bestAsk().getPrice(), 0.0001);
    }

    @Test
    void samePriceUsesTimePriority() {
        OrderBook book = new OrderBook("AAPL");
        book.add(buy(150.0, 10));
        book.add(buy(150.0, 5));
        // 同价时先到者（序号小）优先
        assertEquals(5, book.bestBid().getId());
    }

    @Test
    void aggregatesBidLevels() {
        OrderBook book = new OrderBook("AAPL");
        book.add(buy(150.0, 1));
        book.add(buy(150.0, 2));
        book.add(buy(149.0, 3));
        List<PriceLevel> levels = book.getBidLevels(5);
        assertEquals(2, levels.size());
        assertEquals(150.0, levels.get(0).price(), 0.0001);
        assertEquals(200, levels.get(0).quantity());
        assertEquals(2, levels.get(0).orderCount());
    }
}
