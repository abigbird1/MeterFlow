package com.meterflow.billing.service;

import com.meterflow.billing.entity.UsageEvent;
import com.meterflow.billing.feign.PlatformClient;
import com.meterflow.billing.mapper.UsageEventMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** AnomalyService 单测：消费异常检测阈值（Nacos 动态下发）的边界行为。
 *  桩语义：DB cost 为 DECIMAL 元（字符串出参），groupSpend 内部 toCents(元)→分。 */
@ExtendWith(MockitoExtension.class)
class AnomalyServiceTest {

    @Mock private UsageEventMapper usageEventMapper;
    @Mock private PlatformClient platformClient;

    private AnomalyService service;

    private final LocalDate yesterday = LocalDate.of(2026, 11, 7);

    @BeforeEach
    void setUp() {
        service = new AnomalyService(usageEventMapper, platformClient);
        service.ratio = 5;
        service.floorCents = 1000;
    }

    /** 元字符串构造 DB 行（cost DECIMAL 出参语义），toYuan = 分入参转元字符串 */
    private static String toYuanStr(long cents) {
        return java.math.BigDecimal.valueOf(cents, 2).toPlainString();
    }

    private static List<Map<String, Object>> toRows(Map<Long, String> spend) {
        return spend.entrySet().stream()
                .map(e -> Map.<String, Object>of("tenant_id", e.getKey(), "cost", e.getValue()))
                .toList();
    }

    private void stubSpends(Map<Long, String> daySpend, Map<Long, String> weekSpend) {
        when(usageEventMapper.selectMaps(any()))
                .thenReturn(toRows(daySpend), toRows(weekSpend));
    }

    @Test
    @DisplayName("昨日消费 > 前七日日均 × 阈值 → 告警（含倍数与金额）")
    void alertsOnSpike() {
        stubSpends(
                Map.of(1001L, toYuanStr(500000L)),   // 昨日 5000 元
                Map.of(1001L, toYuanStr(140000L)));  // 前 7 日合计 1400 元 → 日均 200，5 倍=1000 < 5000 ✓
        int n = service.scan(yesterday);
        assertThat(n).isEqualTo(1);
        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
        verify(platformClient).notify(cap.capture());
        assertThat(cap.getValue().get("tenantId")).isEqualTo(1001L);
        assertThat(String.valueOf(cap.getValue().get("content"))).contains("5000.00 元");
    }

    @Test
    @DisplayName("未超阈值（昨日 ≤ 日均×5）→ 不告警")
    void noAlertBelowRatio() {
        stubSpends(
                Map.of(1001L, toYuanStr(90000L)),    // 昨日 900 元
                Map.of(1001L, toYuanStr(140000L)));  // 日均 200 → 5 倍 1000 ≥ 900 → 不告警
        assertThat(service.scan(yesterday)).isZero();
        verify(platformClient, org.mockito.Mockito.never()).notify(any());
    }

    @Test
    @DisplayName("绝对下限：昨日消费 ≤ floor-cents → 即使倍数超限也不告警")
    void noAlertBelowFloor() {
        service.floorCents = 100000;                 // 1000 元下限
        stubSpends(
                Map.of(1001L, toYuanStr(50000L)),    // 昨日 500 元（倍数超限但低于下限）
                Map.of(1001L, toYuanStr(14000L)));
        assertThat(service.scan(yesterday)).isZero();
    }

    @Test
    @DisplayName("首次消费租户（基线日均 0）→ 告警（无历史可比）")
    void alertsWhenBaselineZero() {
        stubSpends(Map.of(1002L, toYuanStr(500000L)), Map.of());
        assertThat(service.scan(yesterday)).isEqualTo(1);
    }

    @Test
    @DisplayName("阈值动态化（Nacos @RefreshScope）：改 ratio 后行为随之变化")
    void ratioIsDynamic() {
        // ratio=50：5000 元 vs 日均 200×50=10000 → 不告警
        service.ratio = 50;
        stubSpends(Map.of(1001L, toYuanStr(500000L)), Map.of(1001L, toYuanStr(140000L)));
        assertThat(service.scan(yesterday)).isZero();
        // ratio=1：5000 > 200×1 → 告警
        service.ratio = 1;
        stubSpends(Map.of(1001L, toYuanStr(500000L)), Map.of(1001L, toYuanStr(140000L)));
        assertThat(service.scan(yesterday)).isEqualTo(1);
        verify(platformClient, times(1)).notify(any());
    }

    @Test
    @DisplayName("多租户隔离：只有超标租户收到告警")
    void onlyOffendingTenantNotified() {
        stubSpends(
                Map.of(1001L, toYuanStr(500000L), 1002L, toYuanStr(10000L)),   // 昨日：1001 突增 / 1002 正常
                Map.of(1001L, toYuanStr(140000L), 1002L, toYuanStr(140000L)));
        assertThat(service.scan(yesterday)).isEqualTo(1);
        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
        verify(platformClient, times(1)).notify(cap.capture());
        assertThat(cap.getValue().get("tenantId")).isEqualTo(1001L);
    }
}
