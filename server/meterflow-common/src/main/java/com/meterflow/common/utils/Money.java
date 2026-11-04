package com.meterflow.common.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 金额精度工具（需求 3.9 + 规范 §4）：过程金额 4 位小数（元），入账 HALF_UP 到分（评审 ET1：先合并再舍入） */
public final class Money {

    private static final int PROCESS_SCALE = 4;

    private Money() {}

    /** 过程金额（逐笔计价/抵扣）保留 4 位小数 */
    public static BigDecimal process(BigDecimal yuan) {
        return yuan.setScale(PROCESS_SCALE, RoundingMode.HALF_UP);
    }

    /** 过程金额（元）→ 分，HALF_UP；调用方须保证已合并总费用后再调用（ET1） */
    public static long toCents(BigDecimal yuan) {
        return yuan.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /** 分 → 元（展示两位） */
    public static BigDecimal centsToYuan(long cents) {
        return BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
