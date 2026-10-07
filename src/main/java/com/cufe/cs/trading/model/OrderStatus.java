package com.cufe.cs.trading.model;

/** 订单生命周期状态。 */
public enum OrderStatus {
    /** 已受理、挂入订单簿待成交。 */
    NEW,
    /** 部分成交，剩余部分仍在订单簿。 */
    PARTIALLY_FILLED,
    /** 全部成交。 */
    FILLED,
    /** 已撤销。 */
    CANCELLED,
    /** 因资金/持仓不足等原因被拒绝。 */
    REJECTED
}
