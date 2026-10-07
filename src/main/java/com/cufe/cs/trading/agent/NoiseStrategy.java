package com.cufe.cs.trading.agent;

import com.cufe.cs.trading.model.Side;

import java.util.Random;

/**
 * 内置噪声策略：LLM 不可用（无 API Key 或请求失败）时的回退策略。
 * 以一定概率在最新价附近随机挂限价单，制造双向流动性。
 */
public class NoiseStrategy implements Strategy {
    private final Random random = new Random();

    @Override
    public Decision decide(MarketContext context) {
        if (random.nextDouble() < 0.35) {
            return Decision.hold();
        }
        boolean buy = random.nextBoolean();
        double price = context.lastPrice() * (1 + (random.nextDouble() - 0.5) * 0.02);
        int quantity = 1 + random.nextInt(20);
        return new Decision(buy ? Side.BUY : Side.SELL, price, quantity);
    }
}
