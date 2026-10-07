package com.cufe.cs.trading.core;

/** 订单因业务原因被拒绝（资金或持仓不足）。 */
public class OrderRejectedException extends RuntimeException {

    public OrderRejectedException(String message) {
        super(message);
    }
}
