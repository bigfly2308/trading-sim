package com.cufe.cs.trading.core;

import com.cufe.cs.trading.model.Order;
import com.cufe.cs.trading.model.Trade;

/**
 * 市场事件监听器：用于 CSV 日志、行情广播等。
 * 由撮合引擎在订单最终状态确定、每笔成交发生时回调。
 */
public interface MarketListener {

    /** 一条订单处理完毕（含被拒绝/撤销）时回调。 */
    void onOrder(Order order);

    /** 每笔成交产生时回调。 */
    void onTrade(Trade trade);
}
