package com.meterflow.billing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meterflow.billing.entity.Budget;
import com.meterflow.billing.entity.UsageEvent;
import com.meterflow.billing.feign.PlatformClient;
import com.meterflow.billing.mapper.BudgetMapper;
import com.meterflow.billing.mapper.UsageEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 租户月度预算与告警：设置/查看 + 周期扫描（80%/100% 站内告警，标记位防重复） */
@Service
@RequiredArgsConstructor
@Slf4j
public class BudgetService {

    private final BudgetMapper budgetMapper;
    private final UsageEventMapper usageEventMapper;
    private final PlatformClient platformClient;

    private String currentPeriod() {
        return LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
    }

    /** 本期预算状态（含实时消费与占比；未设置时返回 budgetCents=null） */
    public Map<String, Object> status(Long tenantId) {
        String period = currentPeriod();
        LocalDateTime start = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        BigDecimal spent = usageEventMapper.selectList(new LambdaQueryWrapper<UsageEvent>()
                        .eq(UsageEvent::getTenantId, tenantId)
                        .eq(UsageEvent::getStatus, "normal")
                        .ge(UsageEvent::getArriveTime, start))
                .stream().map(UsageEvent::getCostAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        long spentCents = com.meterflow.common.utils.Money.toCents(spent);

        Budget b = budgetMapper.selectOne(new LambdaQueryWrapper<Budget>()
                .eq(Budget::getTenantId, tenantId).eq(Budget::getPeriod, period).last("limit 1"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("period", period);
        out.put("spentCents", spentCents);
        if (b == null) {
            out.put("budgetCents", null);
            return out;
        }
        long budgetCents = b.getBudgetCents();
        out.put("budgetCents", budgetCents);
        out.put("percent", budgetCents > 0 ? Math.min(999, spentCents * 100 / budgetCents) : 0);
        out.put("remainingCents", Math.max(0, budgetCents - spentCents));
        out.put("notified80", Boolean.TRUE.equals(b.getNotified80()));
        out.put("notified100", Boolean.TRUE.equals(b.getNotified100()));
        return out;
    }

    /** 设置本期预算（覆盖式 upsert；新预算重置告警标记，预算上调后应重新告警观察） */
    @Transactional
    public Map<String, Object> upsert(Long tenantId, long budgetCents) {
        String period = currentPeriod();
        Budget b = budgetMapper.selectOne(new LambdaQueryWrapper<Budget>()
                .eq(Budget::getTenantId, tenantId).eq(Budget::getPeriod, period).last("limit 1"));
        if (b == null) {
            b = new Budget();
            b.setTenantId(tenantId); b.setPeriod(period); b.setBudgetCents(budgetCents);
            b.setNotified80(false); b.setNotified100(false);
            b.setCreatedAt(LocalDateTime.now());
            budgetMapper.insert(b);
        } else {
            b.setBudgetCents(budgetCents);
            b.setNotified80(false); b.setNotified100(false);
            b.setUpdatedAt(LocalDateTime.now());
            budgetMapper.updateById(b);
        }
        return status(tenantId);
    }

    /**
     * 周期扫描：本期消费 ≥80% / ≥100% 预算时发送站内告警（标记位防重复）。
     * 由 @Scheduled 每小时驱动；feign 手动触发供运维/测试。
     */
    @Transactional
    public int checkAndNotify() {
        String period = currentPeriod();
        List<Budget> budgets = budgetMapper.selectList(new LambdaQueryWrapper<Budget>()
                .eq(Budget::getPeriod, period));
        int notified = 0;
        for (Budget b : budgets) {
            LocalDateTime start = LocalDate.now().withDayOfMonth(1).atStartOfDay();
            BigDecimal spent = usageEventMapper.selectList(new LambdaQueryWrapper<UsageEvent>()
                            .eq(UsageEvent::getTenantId, b.getTenantId())
                            .eq(UsageEvent::getStatus, "normal")
                            .ge(UsageEvent::getArriveTime, start))
                    .stream().map(UsageEvent::getCostAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            long spentCents = com.meterflow.common.utils.Money.toCents(spent);
            long budgetCents = b.getBudgetCents();
            if (budgetCents <= 0) continue;

            if (spentCents >= budgetCents && !Boolean.TRUE.equals(b.getNotified100())) {
                notify(b.getTenantId(), String.format("本期预算已用尽：消费 %.2f 元 / 预算 %.2f 元（100%%），请及时充值或调整预算", spentCents / 100.0, budgetCents / 100.0));
                b.setNotified100(true);
                b.setNotified80(true);   // 100% 已覆盖 80% 阈值，避免下一轮再补发 80% 告警
                b.setUpdatedAt(LocalDateTime.now());
                budgetMapper.updateById(b);
                notified++;
            } else if (spentCents >= budgetCents * 80 / 100 && !Boolean.TRUE.equals(b.getNotified80())) {
                notify(b.getTenantId(), String.format("本期预算已使用 %d%%（%.2f / %.2f 元），请注意消费节奏", spentCents * 100 / budgetCents, spentCents / 100.0, budgetCents / 100.0));
                b.setNotified80(true);
                b.setUpdatedAt(LocalDateTime.now());
                budgetMapper.updateById(b);
                notified++;
            }
        }
        return notified;
    }

    private void notify(Long tenantId, String content) {
        try {
            platformClient.notify(Map.of("tenantId", tenantId, "type", "warn",
                    "title", "月度预算告警", "content", content));
        } catch (Exception e) {
            log.warn("[BUDGET] 通知触达失败 tenantId={}：{}", tenantId, e.getMessage());
        }
    }
}
