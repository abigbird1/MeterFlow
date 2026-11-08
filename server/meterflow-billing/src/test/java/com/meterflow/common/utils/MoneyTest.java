package com.meterflow.common.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Money.toCents 单测：元→分精确转换（规范 §3：确定性金额 *_cents） */
class MoneyTest {

    @Test
    @DisplayName("常规金额：两位小数精确转分")
    void basic() {
        assertThat(Money.toCents(new BigDecimal("961.11"))).isEqualTo(96111L);
        assertThat(Money.toCents(new BigDecimal("0.01"))).isEqualTo(1L);
        assertThat(Money.toCents(new BigDecimal("0"))).isZero();
    }

    @Test
    @DisplayName("过程金额 4 位小数：HALF_UP 舍入到分（R15 精度口径）")
    void processAmountRounding() {
        assertThat(Money.toCents(new BigDecimal("0.1000"))).isEqualTo(10L);
        assertThat(Money.toCents(new BigDecimal("0.1050"))).isEqualTo(11L);   // 10.5 分 → HALF_UP 11
        assertThat(Money.toCents(new BigDecimal("0.1049"))).isEqualTo(10L);
        assertThat(Money.toCents(new BigDecimal("1.0050"))).isEqualTo(101L);  // 100.5 → 101
    }

    @Test
    @DisplayName("负数金额：红冲/调整单方向保留")
    void negative() {
        assertThat(Money.toCents(new BigDecimal("-28.40"))).isEqualTo(-2840L);
    }

    @Test
    @DisplayName("大额：超出 int 范围不溢出")
    void large() {
        assertThat(Money.toCents(new BigDecimal("10000000.00"))).isEqualTo(1_000_000_000L);
        assertThat(Money.toCents(new BigDecimal("12345678.90"))).isEqualTo(1_234_567_890L);
    }
}
