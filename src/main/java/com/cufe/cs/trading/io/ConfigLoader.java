package com.cufe.cs.trading.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** 读取 {@code config.properties}，提供带默认值的类型化访问。 */
public class ConfigLoader {
    private final Properties props = new Properties();

    /** 从类路径加载 {@code /config.properties}；找不到则使用内置默认值。 */
    public ConfigLoader() {
        try (InputStream in = ConfigLoader.class.getResourceAsStream("/config.properties")) {
            if (in != null) {
                props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("读取类路径 config.properties 失败", e);
        }
    }

    /** 从指定文件路径加载（供测试或自定义配置）。 */
    public ConfigLoader(Path path) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("读取配置文件失败: " + path, e);
        }
    }

    public String get(String key, String defaultValue) {
        String value = props.getProperty(key);
        return value == null ? defaultValue : value.trim();
    }

    public double getDouble(String key, double defaultValue) {
        String value = props.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public int getInt(String key, int defaultValue) {
        String value = props.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public List<String> getStockSymbols() {
        String value = get("stocks", "AAPL,GOOGL,TSLA");
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public Map<String, Double> getInitialPrices() {
        Map<String, Double> prices = new LinkedHashMap<>();
        for (String symbol : getStockSymbols()) {
            prices.put(symbol, getDouble(symbol, 100.0));
        }
        return prices;
    }

    public int getHumanCount() {
        return getInt("agent.human.count", 2);
    }

    public double getHumanInitialBalance() {
        return getDouble("agent.human.initial.balance", 100000.0);
    }

    public int getAiCount() {
        return getInt("agent.ai.count", 3);
    }

    public double getAiInitialBalance() {
        return getDouble("agent.ai.initial.balance", 50000.0);
    }

    public int getAiDecisionInterval() {
        return getInt("agent.ai.decision.interval", 3);
    }

    public int getInitialShares() {
        return getInt("agent.initial.shares", 100);
    }

    public String getLlmModel() {
        return get("llm.model", "deepseek-chat");
    }

    public double getLlmPriceBoundPct() {
        return getDouble("llm.price.bound.pct", 5.0);
    }

    public double getSimulationSpeed() {
        return getDouble("simulation.speed", 1.0);
    }

    public int getSimulationDuration() {
        return getInt("simulation.duration", 300);
    }

    public String getLlmEndpoint() {
        return get("llm.endpoint", "https://api.deepseek.com/v1/chat/completions");
    }

    public long getLlmRateLimitMs() {
        return getInt("llm.rate.limit.ms", 1000);
    }

    /** 解析 {@code ${ENV_VAR}} 形式的环境变量引用。 */
    public String getLlmApiKey() {
        return resolveEnv(get("llm.api.key", ""));
    }

    static String resolveEnv(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < value.length()) {
            if (value.startsWith("${", i)) {
                int end = value.indexOf('}', i + 2);
                if (end < 0) {
                    result.append(value.substring(i));
                    break;
                }
                String name = value.substring(i + 2, end);
                result.append(System.getenv().getOrDefault(name, ""));
                i = end + 1;
            } else {
                result.append(value.charAt(i));
                i++;
            }
        }
        return result.toString();
    }
}
