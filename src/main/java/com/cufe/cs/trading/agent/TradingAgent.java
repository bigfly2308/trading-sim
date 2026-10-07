package com.cufe.cs.trading.agent;

import com.cufe.cs.trading.core.Account;
import com.cufe.cs.trading.core.MarketData;
import com.cufe.cs.trading.core.MatchingEngine;
import com.cufe.cs.trading.core.OrderRejectedException;
import com.cufe.cs.trading.model.OrderType;
import com.cufe.cs.trading.model.Side;
import com.cufe.cs.trading.model.Trade;

import java.util.List;

/** 交易智能体基类：持有账户与撮合引擎引用，提供下单与停止控制。 */
public abstract class TradingAgent {
    protected final String agentId;
    protected final Account account;
    protected final MatchingEngine engine;
    protected final MarketData marketData;
    protected volatile boolean running = true;

    public TradingAgent(String agentId, Account account, MatchingEngine engine, MarketData marketData) {
        this.agentId = agentId;
        this.account = account;
        this.engine = engine;
        this.marketData = marketData;
    }

    /** 智能体类型：HUMAN / AI。 */
    public abstract String getType();

    public String getAgentId() {
        return agentId;
    }

    public Account getAccount() {
        return account;
    }

    public void stop() {
        running = false;
    }

    /** 下单；被拒绝时返回空列表（业务拒绝不视为错误）。 */
    protected List<Trade> placeOrder(String symbol, Side side, OrderType type, double price, int quantity) {
        try {
            return engine.submitOrder(symbol, side, type, price, quantity, agentId);
        } catch (OrderRejectedException | IllegalArgumentException e) {
            return List.of();
        }
    }
}
