package com.cufe.cs.trading.llm;

import com.cufe.cs.trading.agent.Decision;
import com.cufe.cs.trading.agent.MarketContext;
import com.cufe.cs.trading.io.ConfigLoader;
import com.cufe.cs.trading.model.Side;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * LLM API 客户端（OpenAI 兼容接口，默认 DeepSeek）。
 *
 * <p>未配置 API Key 或请求失败时 {@link #decide(MarketContext)} 返回 {@code null}，
 * 由调用方（AITrader）回退到内置策略。内置限流（默认 1 次/秒）与超时（20s）。</p>
 */
public class LlmClient {
    private static final String SYSTEM_PROMPT =
            "You are a trader in a simulated continuous auction market. "
                    + "Decide whether to BUY, SELL or HOLD one stock based on the market snapshot. "
                    + "Respond with JSON only, in the form "
                    + "{\"action\":\"BUY|SELL|HOLD\",\"price\":<number>,\"quantity\":<integer>}. "
                    + "price and quantity must be positive.";

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String endpoint;
    private final String model;
    private final String apiKey;
    private final long rateLimitMs;
    private final AtomicLong lastRequestAt = new AtomicLong(0);

    public LlmClient(ConfigLoader config) {
        this.endpoint = config.getLlmEndpoint();
        this.model = config.getLlmModel();
        String key = config.getLlmApiKey();
        this.apiKey = key == null ? null : key.trim();
        this.rateLimitMs = config.getLlmRateLimitMs();
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** 诊断用：key 的长度与格式（不泄露 key 本体）。DeepSeek key 形如 sk- + 32 位字母数字。 */
    public String keyDiagnostics() {
        if (!isConfigured()) {
            return "未设置";
        }
        String k = apiKey.trim();
        boolean ok = k.matches("sk-[A-Za-z0-9]{20,}");
        return "长度=" + k.length() + ", 前缀=" + (k.startsWith("sk-") ? "sk-" : "异常")
                + ", 格式=" + (ok ? "正常" : "可疑(可能含 * / 空格 / 被截断)");
    }

    /** 诊断用：key 脱敏预览（前 6 位 + 后 4 位），供用户核对是否为自己的 key。 */
    public String keyMasked() {
        if (!isConfigured()) {
            return "无";
        }
        String k = apiKey;
        if (k.length() <= 12) {
            return "长度过短(" + k.length() + ")";
        }
        return k.substring(0, 6) + "..." + k.substring(k.length() - 4);
    }

    public Decision decide(MarketContext context) {
        if (!isConfigured()) {
            return null;
        }
        try {
            return decideOrThrow(context);
        } catch (Exception e) {
            return null; // 失败回退到内置策略
        }
    }

    /** 与 {@link #decide(MarketContext)} 相同，但失败时抛出异常（供诊断/冒烟测试）。 */
    public Decision decideOrThrow(MarketContext context) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("LLM_API_KEY 未配置");
        }
        enforceRateLimit();
        String content = call(buildUserPrompt(context));
        return parseDecision(content);
    }

    private void enforceRateLimit() {
        long now = System.currentTimeMillis();
        long wait = lastRequestAt.get() + rateLimitMs - now;
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastRequestAt.set(System.currentTimeMillis());
    }

    private String call(String prompt) throws Exception {
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", prompt)),
                "temperature", 0.2);
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP " + response.statusCode());
        }
        JsonNode root = mapper.readTree(response.body());
        return root.path("choices").path(0).path("message").path("content").asText("");
    }

    private String buildUserPrompt(MarketContext ctx) {
        return String.format(
                "Market: %s, Last price: %.2f, Change: %.2f%%.%n"
                        + "Your cash: %.2f, Holdings: %d shares, Return: %.2f%%.%n"
                        + "Best bid: %.2f, Best ask: %.2f.%n"
                        + "Decide: BUY, SELL, or HOLD.",
                ctx.symbol(), ctx.lastPrice(), ctx.changePct(),
                ctx.cash(), ctx.holdings(), ctx.returnRate() * 100,
                ctx.bestBid(), ctx.bestAsk());
    }

    private Decision parseDecision(String content) throws Exception {
        JsonNode node = mapper.readTree(extractJson(content));
        String action = node.path("action").asText("HOLD").trim().toUpperCase();
        if (action.equals("HOLD")) {
            return Decision.hold();
        }
        Side side = action.equals("BUY") ? Side.BUY : action.equals("SELL") ? Side.SELL : null;
        if (side == null) {
            return Decision.hold();
        }
        double price = node.path("price").asDouble(Double.NaN);
        int quantity = node.path("quantity").asInt(0);
        if (Double.isNaN(price) || price <= 0 || quantity <= 0) {
            return Decision.hold();
        }
        return new Decision(side, price, quantity);
    }

    /** 从可能含 Markdown 代码块/额外文本的内容中提取 JSON 对象。 */
    static String extractJson(String content) {
        if (content == null) {
            return "{}";
        }
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }
        return "{}";
    }
}
