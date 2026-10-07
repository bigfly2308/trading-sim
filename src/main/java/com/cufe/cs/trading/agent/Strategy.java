package com.cufe.cs.trading.agent;

/** 交易策略：给定市场快照，给出买卖决策。 */
public interface Strategy {

    Decision decide(MarketContext context);
}
