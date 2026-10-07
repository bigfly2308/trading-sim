package com.cufe.cs.trading;

import com.cufe.cs.trading.core.Account;
import com.cufe.cs.trading.core.AccountManager;
import com.cufe.cs.trading.core.MarketData;
import com.cufe.cs.trading.core.MatchingEngine;
import com.cufe.cs.trading.core.OrderRejectedException;
import com.cufe.cs.trading.io.ConfigLoader;
import com.cufe.cs.trading.io.CsvLogger;
import com.cufe.cs.trading.model.OrderType;
import com.cufe.cs.trading.model.PriceLevel;
import com.cufe.cs.trading.model.Side;
import com.cufe.cs.trading.model.Trade;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** 控制台演示：展示限价/市价撮合、价格发现、撤单与账户结算。 */
public class TradingDemo {

    private static AccountManager accounts;
    private static MatchingEngine engine;
    private static MarketData marketData;

    public static void main(String[] args) {
        // 强制标准输出为 UTF-8，避免 Windows 控制台中文乱码
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));

        ConfigLoader config = new ConfigLoader();
        Map<String, Double> initialPrices = config.getInitialPrices();

        accounts = new AccountManager();
        accounts.register(new Account("humanA", config.getHumanInitialBalance()));
        accounts.register(new Account("aiB", config.getAiInitialBalance(), Map.of("AAPL", 100)));
        accounts.register(new Account("aiC", config.getAiInitialBalance() + 25000, Map.of("AAPL", 100)));

        marketData = new MarketData(initialPrices);
        engine = new MatchingEngine(accounts, marketData);

        System.out.println("===============================================");
        System.out.println("  多智能体连续竞价股票交易模拟 —— 控制台演示");
        System.out.println("===============================================");
        System.out.println("初始价格: " + initialPrices);
        System.out.println();

        try (CsvLogger logger = new CsvLogger(Path.of("logs"))) {
            engine.addListener(logger);
            demoPriceDiscovery();
            printFinalSummary();
        }
        System.out.println("\n成交与委托日志已写入 logs/ 目录。");
    }

    private static void demoPriceDiscovery() {
        System.out.println("[1] aiB 挂卖单: SELL AAPL 50 @ 174.50（无对手，进入订单簿）");
        submit("aiB", Side.SELL, OrderType.LIMIT, "AAPL", 174.50, 50);
        printBook("AAPL");

        System.out.println("[2] humanA 挂买单: BUY AAPL 100 @ 175.00（与卖单 174.50 成交）");
        submit("humanA", Side.BUY, OrderType.LIMIT, "AAPL", 175.00, 100);
        printBook("AAPL");

        System.out.println("[3] aiC 挂卖单: SELL AAPL 80 @ 175.50（不成交，进入订单簿）");
        submit("aiC", Side.SELL, OrderType.LIMIT, "AAPL", 175.50, 80);
        printBook("AAPL");

        System.out.println("[4] humanA 市价买入: BUY AAPL 10 市价（吃掉最低卖价 175.50）");
        submit("humanA", Side.BUY, OrderType.MARKET, "AAPL", Double.NaN, 10);
        printBook("AAPL");

        System.out.println("[5] aiC 撤销剩余卖单");
        List<String> ids = engine.activeOrderIds("aiC");
        if (!ids.isEmpty()) {
            boolean ok = engine.cancelOrder(ids.get(0), "aiC");
            System.out.println("    撤销 " + ids.get(0) + " -> " + ok);
        }
        printBook("AAPL");

        System.out.println("[6] aiB 资金不足下单（应被拒绝）");
        submit("aiB", Side.BUY, OrderType.LIMIT, "AAPL", 99999.0, 100);
    }

    private static void submit(String agentId, Side side, OrderType type, String symbol,
                               double price, int quantity) {
        try {
            List<Trade> trades = engine.submitOrder(symbol, side, type, price, quantity, agentId);
            for (Trade trade : trades) {
                System.out.println("    成交: " + trade);
            }
        } catch (OrderRejectedException e) {
            System.out.println("    拒绝: " + e.getMessage());
        }
    }

    private static void printBook(String symbol) {
        var book = engine.getBook(symbol);
        System.out.println("    ---- " + symbol + " 订单簿 (最新价 " +
                String.format("%.2f", engine.getLastPrice(symbol)) + ") ----");
        List<PriceLevel> asks = book.getAskLevels(5);
        for (int i = asks.size() - 1; i >= 0; i--) {
            System.out.printf("    SELL %8.2f  x %d%n", asks.get(i).price(), asks.get(i).quantity());
        }
        List<PriceLevel> bids = book.getBidLevels(5);
        for (PriceLevel level : bids) {
            System.out.printf("    BUY  %8.2f  x %d%n", level.price(), level.quantity());
        }
        System.out.println();
    }

    private static void printFinalSummary() {
        System.out.println("===============================================");
        System.out.println("账户结算汇总");
        System.out.println("===============================================");
        Map<String, Double> prices = marketData.priceSnapshot();
        for (Account account : accounts.all()) {
            double equity = account.computeEquity(prices);
            double rate = account.computeReturnRate(prices);
            System.out.printf("  %-8s 现金 %12.2f  持仓 %-24s 总资产 %12.2f  收益率 %+.2f%%%n",
                    account.getAgentId(), account.getCash(),
                    account.getHoldingsSnapshot(), equity, rate * 100);
        }
    }
}
