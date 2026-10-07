package com.cufe.cs.trading.core;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/** 市场行情：每只股票的最新成交价、累计成交量与价格历史。 */
public class MarketData {

    /** 一个价格历史点（用于价格图）。 */
    public record PricePoint(long seq, double price) {
    }

    private static final int HISTORY_LIMIT = 500;

    private final Map<String, Double> initialPrices;
    private final Map<String, Double> lastPrices = new ConcurrentHashMap<>();
    private final Map<String, Long> volumes = new ConcurrentHashMap<>();
    private final Map<String, Deque<PricePoint>> history = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong(0);

    public MarketData(Map<String, Double> initialPrices) {
        this.initialPrices = new ConcurrentHashMap<>(initialPrices);
        for (Map.Entry<String, Double> e : initialPrices.entrySet()) {
            Deque<PricePoint> points = new ConcurrentLinkedDeque<>();
            points.add(new PricePoint(0, e.getValue()));
            history.put(e.getKey(), points);
        }
    }

    /** 当前价 = 最新成交价；若无成交则回退到初始价。 */
    public double getPrice(String symbol) {
        Double last = lastPrices.get(symbol);
        return last != null ? last : initialPrices.getOrDefault(symbol, 0.0);
    }

    public double getInitialPrice(String symbol) {
        return initialPrices.getOrDefault(symbol, 0.0);
    }

    public Double getLastPrice(String symbol) {
        return lastPrices.get(symbol);
    }

    public long getVolume(String symbol) {
        return volumes.getOrDefault(symbol, 0L);
    }

    /** 记录一笔成交，更新最新价、成交量与价格历史。 */
    public void recordTrade(String symbol, double price, int quantity) {
        lastPrices.put(symbol, price);
        volumes.merge(symbol, (long) quantity, Long::sum);
        Deque<PricePoint> points = history.computeIfAbsent(symbol, k -> new ConcurrentLinkedDeque<>());
        points.addLast(new PricePoint(seq.incrementAndGet(), price));
        while (points.size() > HISTORY_LIMIT) {
            points.pollFirst();
        }
    }

    /** 某 symbol 的价格历史（按时间顺序）。 */
    public List<PricePoint> getHistory(String symbol) {
        Deque<PricePoint> points = history.get(symbol);
        return points == null ? List.of() : new ArrayList<>(points);
    }

    /** 全部 symbol 的当前价快照（供计算总资产）。 */
    public Map<String, Double> priceSnapshot() {
        Map<String, Double> snapshot = new HashMap<>(initialPrices);
        snapshot.putAll(lastPrices);
        return snapshot;
    }
}
