package com.cufe.cs.trading.core;

import com.cufe.cs.trading.model.Order;
import com.cufe.cs.trading.model.PriceLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

/**
 * 单一股票的订单簿：两个优先队列。
 *
 * <ul>
 *   <li>买单（bids）：价格高者优先；同价时先到者（序号小）优先。</li>
 *   <li>卖单（asks）：价格低者优先；同价时先到者（序号小）优先。</li>
 * </ul>
 */
public class OrderBook {
    private final String symbol;

    private final PriorityQueue<Order> bids = new PriorityQueue<>((a, b) -> {
        int byPrice = Double.compare(b.getPrice(), a.getPrice()); // 买价降序
        return byPrice != 0 ? byPrice : Long.compare(a.getId(), b.getId());
    });

    private final PriorityQueue<Order> asks = new PriorityQueue<>((a, b) -> {
        int byPrice = Double.compare(a.getPrice(), b.getPrice()); // 卖价升序
        return byPrice != 0 ? byPrice : Long.compare(a.getId(), b.getId());
    });

    public OrderBook(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }

    public Order bestBid() {
        return bids.peek();
    }

    public Order bestAsk() {
        return asks.peek();
    }

    public void add(Order order) {
        if (order.isBuy()) {
            bids.add(order);
        } else {
            asks.add(order);
        }
    }

    public boolean remove(Order order) {
        return order.isBuy() ? bids.remove(order) : asks.remove(order);
    }

    public int bidCount() {
        return bids.size();
    }

    public int askCount() {
        return asks.size();
    }

    public boolean isEmpty() {
        return bids.isEmpty() && asks.isEmpty();
    }

    /** 撮合引擎内部使用的原始队列（同包访问）。 */
    PriorityQueue<Order> bidsQueue() {
        return bids;
    }

    PriorityQueue<Order> asksQueue() {
        return asks;
    }

    /** 前 {@code n} 档聚合的买价档位（用于深度图）。 */
    public List<PriceLevel> getBidLevels(int n) {
        return aggregate(bids, n);
    }

    /** 前 {@code n} 档聚合的卖价档位（用于深度图）。 */
    public List<PriceLevel> getAskLevels(int n) {
        return aggregate(asks, n);
    }

    private List<PriceLevel> aggregate(PriorityQueue<Order> queue, int n) {
        List<Order> sorted = new ArrayList<>(queue);
        sorted.sort(queue.comparator());
        List<PriceLevel> levels = new ArrayList<>();
        for (Order order : sorted) {
            if (levels.size() >= n) {
                break;
            }
            if (!levels.isEmpty() && levels.get(levels.size() - 1).price() == order.getPrice()) {
                PriceLevel last = levels.remove(levels.size() - 1);
                levels.add(new PriceLevel(last.price(), last.quantity() + order.getRemaining(), last.orderCount() + 1));
            } else {
                levels.add(new PriceLevel(order.getPrice(), order.getRemaining(), 1));
            }
        }
        return levels;
    }
}
