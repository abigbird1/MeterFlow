package com.meterflow.billing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meterflow.billing.entity.*;
import com.meterflow.billing.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 租户总览聚合（05 §5.1.1）：余额/本月消费/事件数/额度/赠送/资源包/7 日趋势 */
@Service
@RequiredArgsConstructor
public class OverviewService {

    private final BalanceAccountMapper accountMapper;
    private final UsageEventMapper usageEventMapper;
    private final FreeQuotaMapper freeQuotaMapper;
    private final GiftGrantMapper giftGrantMapper;
    private final PackageInstanceMapper packageMapper;
    private final ProductMapper productMapper;
    private final ProductMetricMapper metricMapper;

    public Map<String, Object> overview(Long tenantId, Long memberDeptId) {
        Map<String, Object> out = new LinkedHashMap<>();
        BalanceAccount acc = accountMapper.selectOne(new LambdaQueryWrapper<BalanceAccount>()
                .eq(BalanceAccount::getTenantId, tenantId).last("limit 1"));

        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        // 部门级数据隔离：成员仅统计本部门（管理员/财务 memberDeptId=null 全租户）
        // SQL 按日聚合一次取回（月汇总 + 7 日趋势同源），避免全量实体入内存（规范 #5.9）
        var qw = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UsageEvent>()
                .select("DATE(arrive_time) AS d", "SUM(cost_amount) AS c", "COUNT(*) AS n")
                .eq("tenant_id", tenantId)
                .ge("arrive_time", monthStart);
        if (memberDeptId != null) qw.eq("dept_id", memberDeptId);
        qw.groupBy("DATE(arrive_time)");
        List<Map<String, Object>> rows = usageEventMapper.selectMaps(qw);

        BigDecimal monthSpend = BigDecimal.ZERO;
        long eventCount = 0;
        java.time.LocalDate trendFrom = LocalDate.now().minusDays(7);
        Map<String, BigDecimal> byDay = new java.util.LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            BigDecimal c = new BigDecimal(String.valueOf(r.get("c")));
            long n = Long.parseLong(String.valueOf(r.get("n")));
            String d = String.valueOf(r.get("d"));
            monthSpend = monthSpend.add(c);
            eventCount += n;
            if (LocalDate.parse(d).isAfter(trendFrom)) byDay.put(d, c);
        }
        out.put("eventCount", (int) eventCount);
        out.put("monthSpendAmount", monthSpend.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
        // 7 日趋势（元/日）
        out.put("dailyTrend", byDay);

        // 上月同期（1 日 ~ 本月同日）累计消费，供环比展示
        LocalDateTime lastStart = monthStart.minusMonths(1);
        LocalDateTime lastEnd = lastStart.plusDays(LocalDate.now().getDayOfMonth());
        var qwLast = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UsageEvent>()
                .select("IFNULL(SUM(cost_amount),0) AS total")
                .eq("tenant_id", tenantId)
                .ge("arrive_time", lastStart).lt("arrive_time", lastEnd);
        if (memberDeptId != null) qwLast.eq("dept_id", memberDeptId);
        List<Map<String, Object>> lastRows = usageEventMapper.selectMaps(qwLast);
        BigDecimal lastSpend = new BigDecimal(String.valueOf(lastRows.isEmpty() ? BigDecimal.ZERO : lastRows.get(0).get("total")));
        out.put("lastMonthSpendAmount", lastSpend.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
        out.put("monthGrowthPercent", lastSpend.signum() > 0
                ? monthSpend.subtract(lastSpend).multiply(java.math.BigDecimal.valueOf(100)).divide(lastSpend, 0, java.math.RoundingMode.HALF_UP).intValue()
                : null);

        // 余额（成员角色不展示租户级余额：置 null 由前端隐藏）
        if (acc != null && memberDeptId == null) {
            out.put("balanceCents", acc.getBalanceCents());
            out.put("overdraftCents", acc.getOverdraftCents());
            out.put("stage", acc.getStatus());
        } else {
            out.put("balanceCents", null);
            out.put("stage", null);
        }

        // 免费额度与赠送（成员不展示）
        if (memberDeptId == null) {
            out.put("quotas", freeQuotaMapper.selectList(new LambdaQueryWrapper<FreeQuota>()
                    .eq(FreeQuota::getTenantId, tenantId).eq(FreeQuota::getPeriod, period())));
            out.put("gifts", giftGrantMapper.selectList(new LambdaQueryWrapper<GiftGrant>()
                    .eq(GiftGrant::getTenantId, tenantId)
                    .gt(GiftGrant::getRemaining, BigDecimal.ZERO)));
            out.put("packages", packageMapper.selectList(new LambdaQueryWrapper<PackageInstance>()
                    .eq(PackageInstance::getTenantId, tenantId)
                    .eq(PackageInstance::getStatus, "active")));
        }
        return out;
    }

    private String period() { return LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")); }
}
