package com.cufe.cs.trading.simulation;

import com.cufe.cs.trading.agent.TradingAgent;
import com.cufe.cs.trading.core.Account;
import com.cufe.cs.trading.core.MarketListener;
import com.cufe.cs.trading.io.ConfigLoader;
import com.cufe.cs.trading.model.Order;
import com.cufe.cs.trading.model.Trade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationTest {

    @Test
    void createsHumanAndAiAgents(@TempDir Path tmp) throws Exception {
        Simulation sim = new Simulation(new ConfigLoader(writeConfig(tmp, 2, 3, 1)));
        int humans = 0;
        int ais = 0;
        for (TradingAgent agent : sim.getAgents()) {
            if ("HUMAN".equals(agent.getType())) {
                humans++;
            } else {
                ais++;
            }
        }
        assertEquals(2, humans);
        assertEquals(3, ais);
        assertEquals(5, sim.getAccounts().size());
        sim.close();
    }

    @Test
    void aiAgentsPlaceOrdersWhenStarted(@TempDir Path tmp) throws Exception {
        Simulation sim = new Simulation(new ConfigLoader(writeConfig(tmp, 1, 3, 1)));
        AtomicInteger orders = new AtomicInteger();
        sim.getEngine().addListener(new MarketListener() {
            @Override
            public void onOrder(Order order) {
                orders.incrementAndGet();
            }

            @Override
            public void onTrade(Trade trade) {
            }
        });

        assertFalse(sim.isRunning());
        sim.start();
        assertTrue(sim.isRunning());
        Thread.sleep(4500);
        sim.stop();
        assertFalse(sim.isRunning());
        assertTrue(orders.get() > 0, "AI 智能体应在运行期间提交订单");
        sim.close();
    }

    @Test
    void seededHoldingsAreApplied(@TempDir Path tmp) throws Exception {
        Simulation sim = new Simulation(new ConfigLoader(writeConfig(tmp, 1, 1, 1)));
        for (Account account : sim.getAccounts().all()) {
            assertEquals(100, account.getHolding("AAPL"), "每个智能体应持有初始 100 股 AAPL");
        }
        sim.close();
    }

    private static Path writeConfig(Path dir, int humans, int ais, int interval) throws Exception {
        String content = "stocks=AAPL\nAAPL=175.50\n"
                + "agent.human.count=" + humans + "\n"
                + "agent.human.initial.balance=100000\n"
                + "agent.ai.count=" + ais + "\n"
                + "agent.ai.initial.balance=50000\n"
                + "agent.ai.decision.interval=" + interval + "\n"
                + "agent.initial.shares=100\n";
        Path file = dir.resolve("config.properties");
        Files.writeString(file, content);
        return file;
    }
}
