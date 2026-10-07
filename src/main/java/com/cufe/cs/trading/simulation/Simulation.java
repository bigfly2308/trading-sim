package com.cufe.cs.trading.simulation;

import com.cufe.cs.trading.agent.AITrader;
import com.cufe.cs.trading.agent.HumanBroker;
import com.cufe.cs.trading.agent.TradingAgent;
import com.cufe.cs.trading.core.Account;
import com.cufe.cs.trading.core.AccountManager;
import com.cufe.cs.trading.core.MarketData;
import com.cufe.cs.trading.core.MatchingEngine;
import com.cufe.cs.trading.io.ConfigLoader;
import com.cufe.cs.trading.io.CsvLogger;
import com.cufe.cs.trading.llm.LlmClient;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 仿真编排：加载配置、创建账户与智能体、启动/停止 AI 交易线程。 */
public class Simulation {
    private final ConfigLoader config;
    private final AccountManager accounts = new AccountManager();
    private final MarketData marketData;
    private final MatchingEngine engine;
    private final CsvLogger logger;
    private final LlmClient llm;
    private final List<TradingAgent> agents = new ArrayList<>();
    private volatile boolean running = false;

    public Simulation(ConfigLoader config) {
        this.config = config;
        this.marketData = new MarketData(config.getInitialPrices());
        this.engine = new MatchingEngine(accounts, marketData);
        this.logger = new CsvLogger(Path.of("logs"));
        this.llm = new LlmClient(config);
        engine.addListener(logger);
        createAgents();
    }

    private void createAgents() {
        List<String> symbols = config.getStockSymbols();
        Map<String, Integer> holdings = new HashMap<>();
        for (String symbol : symbols) {
            holdings.put(symbol, config.getInitialShares());
        }

        for (int i = 0; i < config.getHumanCount(); i++) {
            String id = "human" + (i + 1);
            Account account = new Account(id, config.getHumanInitialBalance(), holdings);
            accounts.register(account);
            agents.add(new HumanBroker(id, account, engine, marketData));
        }

        for (int i = 0; i < config.getAiCount(); i++) {
            String id = "ai" + (i + 1);
            Account account = new Account(id, config.getAiInitialBalance(), holdings);
            accounts.register(account);
            String symbol = symbols.get(i % symbols.size());
            agents.add(new AITrader(id, account, engine, marketData, llm, symbol,
                    config.getAiDecisionInterval(), config.getLlmPriceBoundPct()));
        }
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        for (TradingAgent agent : agents) {
            if (agent instanceof AITrader ai) {
                ai.start();
            }
        }
    }

    public synchronized void stop() {
        running = false;
        for (TradingAgent agent : agents) {
            agent.stop();
        }
    }

    public void close() {
        stop();
        logger.close();
    }

    public MatchingEngine getEngine() {
        return engine;
    }

    public AccountManager getAccounts() {
        return accounts;
    }

    public MarketData getMarketData() {
        return marketData;
    }

    public List<TradingAgent> getAgents() {
        return agents;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isLlmConfigured() {
        return llm.isConfigured();
    }
}
