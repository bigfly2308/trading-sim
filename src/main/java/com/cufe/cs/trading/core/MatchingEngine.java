package com.cufe.cs.trading.core;

import com.cufe.cs.trading.model.Order;
import com.cufe.cs.trading.model.OrderStatus;
import com.cufe.cs.trading.model.OrderType;
import com.cufe.cs.trading.model.Side;
import com.cufe.cs.trading.model.Trade;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 撮合引擎：线程安全地处理订单提交与撤单，按“价格-时间优先”撮合。
 *
 * <p>核心方法 {@link #submitOrder(Order)} 与 {@link #cancelOrder(String, String)} 均
 * {@code synchronized}，保证多智能体并发下单时撮合与结算的原子性。</p>
 */
public class MatchingEngine {
    private final Map<String, OrderBook> books = new ConcurrentHashMap<>();
    private final AccountManager accounts;
    private final MarketData marketData;
    private final AtomicLong orderSequence = new AtomicLong(0);
    private final AtomicLong tradeSequence = new AtomicLong(0);
    private final Map<String, Order> activeOrders = new ConcurrentHashMap<>();
    private final List<MarketListener> listeners = new CopyOnWriteArrayList<>();

    public MatchingEngine(AccountManager accounts, MarketData marketData) {
        this.accounts = accounts;
        this.marketData = marketData;
    }

    public void addListener(MarketListener listener) {
        listeners.add(listener);
    }

    public void removeListener(MarketListener listener) {
        listeners.remove(listener);
    }

    /** 便捷入口：构造并提交一条订单，返回本次产生的成交列表。 */
    public List<Trade> submitOrder(String symbol, Side side, OrderType type, double price,
                                   int quantity, String agentId) {
        return submitOrder(new Order(orderSequence.incrementAndGet(), symbol, side, type, price, quantity, agentId));
    }

    /**
     * 提交订单并撮合。
     *
     * @return 本次订单撮合产生的全部成交
     * @throws InsufficientFundsException  买入时可用资金不足
     * @throws InsufficientSharesException 卖出时持仓不足
     * @throws IllegalArgumentException    参数非法（空 symbol、非正数量/价格、未知账户等）
     */
    public synchronized List<Trade> submitOrder(Order order) {
        validate(order);
        Account account = accounts.require(order.getAgentId());
        OrderBook book = books.computeIfAbsent(order.getSymbol(), OrderBook::new);

        List<Trade> trades = new ArrayList<>();
        try {
            reserve(order, account);
        } catch (OrderRejectedException e) {
            order.setStatus(OrderStatus.REJECTED);
            notifyOrder(order);
            throw e;
        }

        PriorityQueue<Order> opposite = order.isBuy() ? book.asksQueue() : book.bidsQueue();

        while (order.getRemaining() > 0 && !opposite.isEmpty()) {
            Order resting = opposite.peek();
            if (!canMatch(order, resting)) {
                break;
            }
            int qty = Math.min(order.getRemaining(), resting.getRemaining());
            double price = resting.getPrice();

            // 市价买单逐笔扣款，余额不足则停止（部分成交，剩余丢弃）
            if (order.isBuy() && order.isMarket()) {
                if (account.getCash() < Money.round2(price * qty)) {
                    break;
                }
            }

            settle(order, resting, qty, price);
            trades.add(makeTrade(order, resting, qty, price));

            order.fill(qty);
            resting.fill(qty);
            if (resting.isFilled()) {
                opposite.poll();
                activeOrders.remove(resting.getOrderId());
            }
        }

        // 限价单剩余部分挂入订单簿；市价单剩余部分丢弃（卖单退回预留持仓）
        if (order.getRemaining() > 0 && order.getType() == OrderType.LIMIT) {
            book.add(order);
            activeOrders.put(order.getOrderId(), order);
        } else if (order.getRemaining() > 0 && order.isMarket() && !order.isBuy()) {
            account.releaseShares(order.getSymbol(), order.getRemaining());
        }

        notifyOrder(order);
        for (Trade trade : trades) {
            notifyTrade(trade);
        }
        return trades;
    }

    /**
     * 撤单：只能撤销本人仍挂在订单簿上的限价单。
     *
     * @return 是否成功撤销
     */
    public synchronized boolean cancelOrder(String orderId, String agentId) {
        Order order = activeOrders.get(orderId);
        if (order == null || !order.getAgentId().equals(agentId)) {
            return false;
        }
        OrderBook book = books.get(order.getSymbol());
        if (book != null) {
            book.remove(order);
        }
        activeOrders.remove(orderId);

        Account account = accounts.require(agentId);
        if (order.isBuy()) {
            account.releaseCash(Money.round2(order.getPrice() * order.getRemaining()));
        } else {
            account.releaseShares(order.getSymbol(), order.getRemaining());
        }
        order.setStatus(OrderStatus.CANCELLED);
        notifyOrder(order);
        return true;
    }

    /** 某智能体当前挂单的订单号列表（供 GUI / 撤单使用）。 */
    public List<String> activeOrderIds(String agentId) {
        return activeOrders.values().stream()
                .filter(o -> o.getAgentId().equals(agentId))
                .map(Order::getOrderId)
                .toList();
    }

    public OrderBook getBook(String symbol) {
        return books.get(symbol);
    }

    public double getLastPrice(String symbol) {
        return marketData.getPrice(symbol);
    }

    public MarketData getMarketData() {
        return marketData;
    }

    public int activeOrderCount() {
        return activeOrders.size();
    }

    private void validate(Order order) {
        if (order.getSymbol() == null || order.getSymbol().isBlank()) {
            throw new IllegalArgumentException("symbol 不能为空");
        }
        if (order.getSide() == null || order.getType() == null) {
            throw new IllegalArgumentException("side/type 不能为空");
        }
        if (order.getQuantity() <= 0) {
            throw new IllegalArgumentException("数量必须为正: " + order.getQuantity());
        }
        if (order.getType() == OrderType.LIMIT && order.getPrice() <= 0) {
            throw new IllegalArgumentException("限价单价格必须为正: " + order.getPrice());
        }
        if (order.getAgentId() == null || order.getAgentId().isBlank()) {
            throw new IllegalArgumentException("agentId 不能为空");
        }
    }

    private void reserve(Order order, Account account) {
        if (order.isBuy()) {
            if (order.getType() == OrderType.LIMIT) {
                double required = Money.round2(order.getPrice() * order.getQuantity());
                if (!account.reserveCash(required)) {
                    throw new InsufficientFundsException(order.getAgentId(), required, account.getCash());
                }
            }
            // 市价买单不预留现金，撮合时逐笔扣款
        } else if (!account.reserveShares(order.getSymbol(), order.getQuantity())) {
            throw new InsufficientSharesException(order.getAgentId(), order.getSymbol(),
                    order.getQuantity(), account.getHolding(order.getSymbol()));
        }
    }

    private boolean canMatch(Order incoming, Order resting) {
        if (incoming.isBuy()) {
            return incoming.isMarket() || incoming.getPrice() >= resting.getPrice();
        }
        return incoming.isMarket() || incoming.getPrice() <= resting.getPrice();
    }

    /** 按“对手（挂单）价格”成交并结算双方账户。 */
    private void settle(Order incoming, Order resting, int qty, double price) {
        Order buyer = incoming.isBuy() ? incoming : resting;
        Order seller = incoming.isBuy() ? resting : incoming;
        Account buyAccount = accounts.require(buyer.getAgentId());
        Account sellAccount = accounts.require(seller.getAgentId());
        double amount = Money.round2(price * qty);

        if (buyer.isMarket()) {
            // 市价买单：逐笔扣款（余额已在循环中校验）
            buyAccount.deductCash(amount);
            buyAccount.addShares(incoming.getSymbol(), qty);
        } else {
            // 限价买单：下单时已按限价预留现金，此处退回多预留部分
            double refund = Money.round2((buyer.getPrice() - price) * qty);
            if (refund > 0) {
                buyAccount.releaseCash(refund);
            }
            buyAccount.addShares(incoming.getSymbol(), qty);
        }
        // 卖方：下单时已预留持仓，成交后到账现金
        sellAccount.addCash(amount);
    }

    private Trade makeTrade(Order incoming, Order resting, int qty, double price) {
        Order buyer = incoming.isBuy() ? incoming : resting;
        Order seller = incoming.isBuy() ? resting : incoming;
        Trade trade = new Trade(tradeSequence.incrementAndGet(), incoming.getSymbol(), price, qty,
                buyer.getAgentId(), seller.getAgentId(), buyer.getOrderId(), seller.getOrderId());
        marketData.recordTrade(incoming.getSymbol(), price, qty);
        return trade;
    }

    private void notifyOrder(Order order) {
        for (MarketListener listener : listeners) {
            listener.onOrder(order);
        }
    }

    private void notifyTrade(Trade trade) {
        for (MarketListener listener : listeners) {
            listener.onTrade(trade);
        }
    }
}
