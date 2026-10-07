package com.cufe.cs.trading.model;

/** 一条成交记录。 */
public class Trade {
    private final long id;
    private final String symbol;
    private final double price;
    private final int quantity;
    private final String buyAgentId;
    private final String sellAgentId;
    private final String buyOrderId;
    private final String sellOrderId;
    private final long timestamp;

    public Trade(long id, String symbol, double price, int quantity,
                 String buyAgentId, String sellAgentId, String buyOrderId, String sellOrderId) {
        this.id = id;
        this.symbol = symbol;
        this.price = price;
        this.quantity = quantity;
        this.buyAgentId = buyAgentId;
        this.sellAgentId = sellAgentId;
        this.buyOrderId = buyOrderId;
        this.sellOrderId = sellOrderId;
        this.timestamp = System.currentTimeMillis();
    }

    public long getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public double getPrice() {
        return price;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getBuyAgentId() {
        return buyAgentId;
    }

    public String getSellAgentId() {
        return sellAgentId;
    }

    public String getBuyOrderId() {
        return buyOrderId;
    }

    public String getSellOrderId() {
        return sellOrderId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return String.format("TRADE %s %d @ %.2f  (buyer=%s, seller=%s)",
                symbol, quantity, price, buyAgentId, sellAgentId);
    }
}
