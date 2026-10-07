package com.cufe.cs.trading.agent;

import com.cufe.cs.trading.model.Side;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NoiseStrategyTest {

    @Test
    void decisionIsAlwaysValid() {
        NoiseStrategy strategy = new NoiseStrategy();
        MarketContext ctx = new MarketContext("AAPL", 175.0, 0.5, 174.0, 176.0, 10000, 10, 12000, 0.1);
        for (int i = 0; i < 200; i++) {
            Decision d = strategy.decide(ctx);
            if (!d.isHold()) {
                assertTrue(d.side() == Side.BUY || d.side() == Side.SELL);
                assertTrue(d.price() > 0, "price 应为正");
                assertTrue(d.quantity() > 0, "quantity 应为正");
            }
        }
    }
}
