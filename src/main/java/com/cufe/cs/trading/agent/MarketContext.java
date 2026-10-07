package com.cufe.cs.trading.agent;

/** 智能体决策所需的行情与账户快照。 */
public record MarketContext(
        String symbol,
        double lastPrice,
        double changePct,
        double bestBid,
        double bestAsk,
        double cash,
        int holdings,
        double equity,
        double returnRate) {
}
