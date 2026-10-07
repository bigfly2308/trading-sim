package com.cufe.cs.trading.core;

/** 下单时账户持仓不足。 */
public class InsufficientSharesException extends OrderRejectedException {

    public InsufficientSharesException(String agentId, String symbol, int required, int available) {
        super(String.format("Agent %s 持仓不足: %s 需要 %d 股, 可用 %d 股", agentId, symbol, required, available));
    }
}
