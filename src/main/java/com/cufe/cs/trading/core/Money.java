package com.cufe.cs.trading.core;

/** 金额工具：保留两位小数的四舍五入。 */
public final class Money {

    private Money() {
    }

    public static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
