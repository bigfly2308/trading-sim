package com.cufe.cs.trading.agent;

import com.cufe.cs.trading.model.Side;

/**
 * 智能体的一次交易决策。
 *
 * @param side     交易方向（{@code null} 表示 HOLD）
 * @param price    限价（HOLD 时为 0）
 * @param quantity 数量（HOLD 时为 0）
 */
public record Decision(Side side, double price, int quantity) {

    public static Decision hold() {
        return new Decision(null, 0, 0);
    }

    public boolean isHold() {
        return side == null || quantity <= 0;
    }
}
