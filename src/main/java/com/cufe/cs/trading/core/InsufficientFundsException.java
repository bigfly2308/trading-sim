package com.cufe.cs.trading.core;

/** 下单时账户可用资金不足。 */
public class InsufficientFundsException extends OrderRejectedException {

    public InsufficientFundsException(String agentId, double required, double available) {
        super(String.format("Agent %s 资金不足: 需要 %.2f, 可用 %.2f", agentId, required, available));
    }
}
