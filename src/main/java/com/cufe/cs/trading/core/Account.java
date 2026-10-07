package com.cufe.cs.trading.core;

import java.util.HashMap;
import java.util.Map;

/**
 * 交易账户：现金与持仓。所有结算方法线程安全。
 *
 * <p>撮合时对挂单采用“预留”机制：限价买单下单即冻结 {@code 限价 × 数量} 现金、
 * 卖单下单即冻结对应持仓，防止同一资金/持仓被重复下单；成交时按实际价格结算并退回
 * 多预留的部分，撤单时退回剩余预留。</p>
 */
public class Account {
    private final String agentId;
    private final double initialBalance;
    private double cash;
    private final Map<String, Integer> holdings;

    public Account(String agentId, double initialBalance) {
        this(agentId, initialBalance, Map.of());
    }

    public Account(String agentId, double initialBalance, Map<String, Integer> initialHoldings) {
        this.agentId = agentId;
        this.initialBalance = Money.round2(initialBalance);
        this.cash = this.initialBalance;
        this.holdings = new HashMap<>(initialHoldings);
    }

    public String getAgentId() {
        return agentId;
    }

    public double getInitialBalance() {
        return initialBalance;
    }

    public synchronized double getCash() {
        return cash;
    }

    /** 预留现金（下单时冻结）；不足返回 {@code false}。 */
    public synchronized boolean reserveCash(double amount) {
        if (amount < 0 || cash < amount) {
            return false;
        }
        cash = Money.round2(cash - amount);
        return true;
    }

    /** 释放现金（撤单或退回多预留部分）。 */
    public synchronized void releaseCash(double amount) {
        if (amount <= 0) {
            return;
        }
        cash = Money.round2(cash + amount);
    }

    /** 扣减现金（市价买单逐笔成交时用）；不足返回 {@code false}。 */
    public synchronized boolean deductCash(double amount) {
        if (amount < 0 || cash < amount) {
            return false;
        }
        cash = Money.round2(cash - amount);
        return true;
    }

    /** 增加现金（卖出成交到账）。 */
    public synchronized void addCash(double amount) {
        cash = Money.round2(cash + amount);
    }

    public synchronized int getHolding(String symbol) {
        return holdings.getOrDefault(symbol, 0);
    }

    /** 预留持仓（卖单下单时冻结）；不足返回 {@code false}。 */
    public synchronized boolean reserveShares(String symbol, int quantity) {
        int current = holdings.getOrDefault(symbol, 0);
        if (quantity < 0 || current < quantity) {
            return false;
        }
        holdings.put(symbol, current - quantity);
        return true;
    }

    /** 释放持仓（撤单退回）。 */
    public synchronized void releaseShares(String symbol, int quantity) {
        if (quantity <= 0) {
            return;
        }
        holdings.merge(symbol, quantity, Integer::sum);
    }

    /** 增加持仓（买入成交）。 */
    public synchronized void addShares(String symbol, int quantity) {
        holdings.merge(symbol, quantity, Integer::sum);
    }

    public synchronized Map<String, Integer> getHoldingsSnapshot() {
        return new HashMap<>(holdings);
    }

    /** 总资产 = 现金 + Σ(持仓 × 现价)。 */
    public synchronized double computeEquity(Map<String, Double> prices) {
        double equity = cash;
        for (Map.Entry<String, Integer> e : holdings.entrySet()) {
            equity += e.getValue() * prices.getOrDefault(e.getKey(), 0.0);
        }
        return Money.round2(equity);
    }

    /** 收益率 = (总资产 − 初始资金) / 初始资金（保留原始精度，展示时再格式化）。 */
    public synchronized double computeReturnRate(Map<String, Double> prices) {
        if (initialBalance == 0) {
            return 0.0;
        }
        return (computeEquity(prices) - initialBalance) / initialBalance;
    }
}
