package com.cufe.cs.trading.llm;

import com.cufe.cs.trading.agent.Decision;
import com.cufe.cs.trading.agent.MarketContext;
import com.cufe.cs.trading.io.ConfigLoader;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/** LLM 连通性冒烟测试：发一次真实请求，验证 key / 端点 / JSON 解析是否正常。 */
public class LlmCheck {

    public static void main(String[] args) {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));

        ConfigLoader config = new ConfigLoader();
        LlmClient client = new LlmClient(config);

        System.out.println("LLM 端点: " + config.getLlmEndpoint());
        System.out.println("模型: " + config.getLlmModel());
        System.out.println("API Key 已配置: " + client.isConfigured());

        if (!client.isConfigured()) {
            System.out.println("结果: 未检测到 LLM_API_KEY，将使用内置噪声策略。");
            return;
        }

        System.out.println("Key 诊断: " + client.keyDiagnostics());
        String rawKey = config.getLlmApiKey();
        System.out.println("Key 预览: " + client.keyMasked()
                + " (环境变量原始长度=" + (rawKey == null ? 0 : rawKey.length()) + ")");
        if (rawKey != null && rawKey.length() != rawKey.trim().length()) {
            System.out.println("警告: 检测到 key 首尾含空格/换行，已自动去除");
        }

        MarketContext ctx = new MarketContext("AAPL", 175.50, 0.0, 174.00, 176.00, 50000, 0, 50000, 0.0);
        try {
            Decision decision = client.decideOrThrow(ctx);
            System.out.println("结果: API 调用成功");
            System.out.println("决策: " + (decision.isHold()
                    ? "HOLD"
                    : decision.side() + " @ " + decision.price() + " x " + decision.quantity()));
        } catch (Exception e) {
            System.out.println("结果: API 调用失败 -> " + e);
            System.out.println("请检查: 1) key 是否完整正确 2) 网络能否访问 api.deepseek.com 3) 账户余额是否充足");
        }
    }
}
