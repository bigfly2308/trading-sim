package com.cufe.cs.trading.model;

/**
 * 一条订单。
 *
 * <p>{@code price} 与 {@code id}（全局自增序号）在撮合排序中保持不变，{@code remaining}
 * 表示未成交数量、可随成交减少，因此可在优先队列中被安全地原地修改。
 * 市价单的 {@code price} 为 {@link Double#NaN}。</p>
 */
public class Order {
    private final long id;         // 全局自增序号，兼作“先到先成交”的时间优先判据
    private final String orderId;  // 形如 ORD-<id>
    private final String symbol;
    private final Side side;
    private final OrderType type;
    private final double price;    // 市价单为 NaN
    private final int quantity;    // 原始数量
    private int remaining;         // 未成交数量
    private final String agentId;
    private final long timestamp;  // 提交时刻（毫秒）
    private OrderStatus status;

    public Order(long id, String symbol, Side side, OrderType type, double price,
                 int quantity, String agentId) {
        this.id = id;
        this.orderId = "ORD-" + id;
        this.symbol = symbol;
        this.side = side;
        this.type = type;
        this.price = (type == OrderType.MARKET) ? Double.NaN : price;
        this.quantity = quantity;
        this.remaining = quantity;
        this.agentId = agentId;
        this.timestamp = System.currentTimeMillis();
        this.status = OrderStatus.NEW;
    }

    public long getId() {
        return id;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getSymbol() {
        return symbol;
    }

    public Side getSide() {
        return side;
    }

    public OrderType getType() {
        return type;
    }

    public double getPrice() {
        return price;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getRemaining() {
        return remaining;
    }

    public String getAgentId() {
        return agentId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public boolean isMarket() {
        return type == OrderType.MARKET;
    }

    public boolean isBuy() {
        return side == Side.BUY;
    }

    public boolean isFilled() {
        return remaining == 0;
    }

    /** 成交 {@code qty} 手：扣减剩余数量并推进状态。 */
    public void fill(int qty) {
        if (qty <= 0 || qty > remaining) {
            throw new IllegalArgumentException("非法成交量: " + qty + "（剩余 " + remaining + "）");
        }
        remaining -= qty;
        status = (remaining == 0) ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED;
    }

    @Override
    public String toString() {
        return String.format("%s %s %s %d @ %s (remaining %d)",
                orderId, side, symbol, quantity,
                isMarket() ? "MARKET" : String.valueOf(price), remaining);
    }
}
