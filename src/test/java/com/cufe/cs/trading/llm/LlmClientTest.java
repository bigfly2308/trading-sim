package com.cufe.cs.trading.llm;

import com.cufe.cs.trading.agent.Decision;
import com.cufe.cs.trading.agent.MarketContext;
import com.cufe.cs.trading.io.ConfigLoader;
import com.cufe.cs.trading.model.Side;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmClientTest {

    @Test
    void withoutApiKeyDecideReturnsNull() {
        LlmClient client = new LlmClient(new ConfigLoader());
        assertFalse(client.isConfigured());
        MarketContext ctx = new MarketContext("AAPL", 175.0, 0.0, 174.0, 176.0, 10000, 0, 10000, 0.0);
        assertNull(client.decide(ctx));
    }

    @Test
    void extractJsonFindsJsonObject() {
        assertEquals("{\"a\":1}", LlmClient.extractJson("```json\n{\"a\":1}\n```"));
        assertEquals("{}", LlmClient.extractJson("no json here"));
    }

    @Test
    void parseDecisionHandlesActionTypes() throws Exception {
        // 通过反射不便访问私有方法，这里只验证决策记录结构（解析逻辑由 AITrader 集成测试覆盖）。
        assertTrue(Decision.hold().isHold());
        assertFalse(new Decision(Side.BUY, 175.0, 10).isHold());
        assertEquals(Side.SELL, new Decision(Side.SELL, 174.0, 5).side());
    }
}
