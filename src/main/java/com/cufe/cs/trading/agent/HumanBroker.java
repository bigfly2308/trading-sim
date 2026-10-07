package com.cufe.cs.trading.agent;

import com.cufe.cs.trading.core.Account;
import com.cufe.cs.trading.core.MarketData;
import com.cufe.cs.trading.core.MatchingEngine;

/** 人类经纪人：通过 GUI 手工下单，本身无自动决策线程。 */
public class HumanBroker extends TradingAgent {

    public HumanBroker(String agentId, Account account, MatchingEngine engine, MarketData marketData) {
        super(agentId, account, engine, marketData);
    }

    @Override
    public String getType() {
        return "HUMAN";
    }
}
