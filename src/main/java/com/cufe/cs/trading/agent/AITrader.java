package com.cufe.cs.trading.agent;

import com.cufe.cs.trading.core.Account;
import com.cufe.cs.trading.core.MarketData;
import com.cufe.cs.trading.core.MatchingEngine;
import com.cufe.cs.trading.core.OrderBook;
import com.cufe.cs.trading.llm.LlmClient;
import com.cufe.cs.trading.model.OrderType;
import com.cufe.cs.trading.model.Side;

import java.util.Map;

/**
 * AI 交易智能体（LLM 驱动 + 内置回退）。
 *
 * <p>独立线程按固定间隔运行：读取行情 → 查询 LLM（失败则用 {@link NoiseStrategy}）→
 * 校验并下单。LLM 报价会被限制在最新价的 ±{@code priceBoundPct}% 以内，数量封顶 100 股。</p>
 */
public class AITrader extends TradingAgent implements Runnable {
    private final LlmClient llm;
    private final Strategy fallback = new NoiseStrategy();
    private final String symbol;
    private final int intervalSec;
    private final double priceBoundPct;
    private Thread thread;

    public AITrader(String agentId, Account account, MatchingEngine engine, MarketData marketData,
                    LlmClient llm, String symbol, int intervalSec, double priceBoundPct) {
        super(agentId, account, engine, marketData);
        this.llm = llm;
        this.symbol = symbol;
        this.intervalSec = Math.max(1, intervalSec);
        this.priceBoundPct = priceBoundPct;
    }

    @Override
    public String getType() {
        return "AI";
    }

    public String getSymbol() {
        return symbol;
    }

    public boolean isLlmConfigured() {
        return llm.isConfigured();
    }

    public synchronized void start() {
        if (thread != null && thread.isAlive()) {
            return;
        }
        thread = new Thread(this, "AI-" + agentId);
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void stop() {
        super.stop();
        if (thread != null) {
            thread.interrupt();
        }
    }

    @Override
    public void run() {
        while (running) {
            try {
                MarketContext context = buildContext();
                Decision decision = llm.decide(context);
                if (decision == null) {
                    decision = fallback.decide(context);
                }
                apply(decision);
            } catch (Exception e) {
                // 单次决策异常不中断线程
            }
            try {
                Thread.sleep(intervalSec * 1000L);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private MarketContext buildContext() {
        double last = marketData.getPrice(symbol);
        double initial = marketData.getInitialPrice(symbol);
        double changePct = initial == 0 ? 0 : (last - initial) / initial * 100;
        OrderBook book = engine.getBook(symbol);
        double bestBid = book != null && book.bestBid() != null ? book.bestBid().getPrice() : last;
        double bestAsk = book != null && book.bestAsk() != null ? book.bestAsk().getPrice() : last;
        Map<String, Double> prices = marketData.priceSnapshot();
        double equity = account.computeEquity(prices);
        double returnRate = account.computeReturnRate(prices);
        return new MarketContext(symbol, last, changePct, bestBid, bestAsk,
                account.getCash(), account.getHolding(symbol), equity, returnRate);
    }

    private void apply(Decision decision) {
        if (decision == null || decision.isHold()) {
            return;
        }
        double last = marketData.getPrice(symbol);
        double bound = last * priceBoundPct / 100.0;
        double price = Math.max(last - bound, Math.min(last + bound, decision.price()));
        if (price <= 0) {
            return;
        }
        int quantity = Math.max(1, Math.min(decision.quantity(), 100));
        if (decision.side() == Side.BUY) {
            if (account.getCash() < price * quantity) {
                return;
            }
        } else if (decision.side() == Side.SELL) {
            if (account.getHolding(symbol) < quantity) {
                return;
            }
        }
        placeOrder(symbol, decision.side(), OrderType.LIMIT, price, quantity);
    }
}
