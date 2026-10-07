package com.cufe.cs.trading.model;

/**
 * 订单簿某一价格档位的聚合信息（供深度图展示）。
 *
 * @param price      价格
 * @param quantity   该档位累计数量
 * @param orderCount 该档位挂单笔数
 */
public record PriceLevel(double price, int quantity, int orderCount) {
}
