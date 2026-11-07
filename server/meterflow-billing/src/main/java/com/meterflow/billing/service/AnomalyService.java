package com.meterflow.billing.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.meterflow.billing.entity.UsageEvent;
import com.meterflow.billing.feign.PlatformClient;
import com.meterflow.billing.mapper.UsageEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 消费异常检测：昨日消费对比前 7 日日均，突增超阈值自动站内提醒。
 *  阈值（倍数/绝对下限）由 Nacos 配置中心动态下发（@RefreshScope，改配置即生效）。 */
@Service
@RequiredArgsConstructor
@Slf4j
@org.springframework.cloud.context.config.annotation.RefreshScope
public class AnomalyService {

    private final UsageEventMapper usageEventMapper;
    private final PlatformClient platformClient;

    @org.springframework.beans.factory.annotation.Value("${meterflow.anomaly.ratio:5}")
    private int ratio;

    @org.springframework.beans.factory.annotation.Value("${meterflow.anomaly.floor-cents:1000}")
    private long floorCents;

    /**
     * 扫描指定业务日的消费异常（每日 00:15 对昨日驱动；feign 手动触发供运维/测试）。
     * 返回发送的告警条数。
     */
    public int scan(LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        LocalDate baselineStart = date.minusDays(7);

        // 昨日各租户消费（分）
        Map<Long, Long> daySpend = groupSpend(start, end);
        // 前 7 日各租户消费合计（分）
        Map<Long, Long> weekSpend = groupSpend(baselineStart.atStartOfDay(), start);

        int notified = 0;
        for (var en : daySpend.entrySet()) {
            long tenantId = en.getKey();
            long today = en.getValue();
            if (today <= floorCents) continue;                        // 绝对下限内不告警
            long avg = weekSpend.getOrDefault(tenantId, 0L) / 7;      // 无消费日均按 0
            if (avg <= 0 || today > avg * ratio) {
                long ratio = avg > 0 ? today / avg : 0;
                notify(tenantId, String.format(
                        "检测到消费异常：%s 消费 %.2f 元，为前 7 日日均（%.2f 元）的 %d 倍。如非预期请检查 API Key 使用与业务调用情况",
                        date, today / 100.0, avg / 100.0, ratio));
                notified++;
            }
        }
        return notified;
    }

    /** 按租户聚合计费金额（分，SQL 聚合规范 #5.9） */
    private Map<Long, Long> groupSpend(LocalDateTime start, LocalDateTime end) {
        var qw = new QueryWrapper<UsageEvent>()
                .select("tenant_id", "IFNULL(SUM(cost_amount),0) AS cost")
                .eq("status", "normal")
                .ge("arrive_time", start).lt("arrive_time", end)
                .groupBy("tenant_id");
        Map<Long, Long> out = new HashMap<>();
        for (Map<String, Object> r : usageEventMapper.selectMaps(qw)) {
            out.put(Long.parseLong(String.valueOf(r.get("tenant_id"))),
                    com.meterflow.common.utils.Money.toCents(new BigDecimal(String.valueOf(r.get("cost")))));
        }
        return out;
    }

    private void notify(Long tenantId, String content) {
        try {
            platformClient.notify(Map.of("tenantId", tenantId, "type", "warn",
                    "title", "消费异常提醒", "content", content));
        } catch (Exception e) {
            log.warn("[ANOMALY] 通知触达失败 tenantId={}：{}", tenantId, e.getMessage());
        }
    }
}
