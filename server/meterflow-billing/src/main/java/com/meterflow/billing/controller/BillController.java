package com.meterflow.billing.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meterflow.billing.entity.DeductionItem;
import com.meterflow.billing.entity.UsageEvent;
import com.meterflow.billing.mapper.DeductionItemMapper;
import com.meterflow.billing.mapper.UsageEventMapper;
import com.meterflow.billing.service.KeyCacheService;
import com.meterflow.billing.service.KeyCacheService.MeContext;
import com.meterflow.common.api.PageResult;
import com.meterflow.common.api.Results;
import com.meterflow.common.api.Results.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 账单与消费明细（05 §5.4）：月度出账在 finance 域接入后，bill 表生成历史账单 */
@RestController
@RequestMapping("/t")
@RequiredArgsConstructor
public class BillController {

    private final KeyCacheService keyCacheService;
    private final UsageEventMapper usageEventMapper;
    private final DeductionItemMapper deductionItemMapper;

    private record Me(Long tenantId, Long memberDeptId) {}

    private Me me(String authorization) {
        MeContext me = keyCacheService.resolveMe(authorization);
        if (me == null || !"t".equals(me.portal())) {
            throw new com.meterflow.common.exception.BusinessException(
                    new com.meterflow.common.exception.ErrorCode() {
                        @Override public String getCode() { return "GWY-0401"; }
                        @Override public String getMessage() { return "登录已失效，请重新登录"; }
                    });
        }
        return new Me(me.tenantId(), me.deptId());
    }

    /** 历史账单（月末出账任务生成；骨架阶段为空列表） */
    @GetMapping("/bills")
    public Result<PageResult<Map<String, Object>>> bills(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                         @RequestParam(defaultValue = "1") int pageNum,
                                                         @RequestParam(defaultValue = "20") int pageSize) {
        me(authorization);
        return Results.ok(new PageResult<>(List.of(), 0, pageNum, pageSize));
    }

    /** 本账期进度（日切快照定案前） */
    @GetMapping("/bills/progress")
    public Result<Map<String, Object>> progress(@RequestHeader(value = "Authorization", required = false) String authorization) {
        Me m = me(authorization);
        var monthStart = java.time.LocalDate.now().withDayOfMonth(1).atStartOfDay();
        // SQL 聚合代替全量实体加载（规范 #5.9：大数据量下避免 OOM/慢查询）
        var qw = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UsageEvent>()
                .select("IFNULL(SUM(cost_amount),0) AS total", "COUNT(*) AS cnt")
                .eq("tenant_id", m.tenantId())
                .ge("arrive_time", monthStart);
        if (m.memberDeptId() != null) qw.eq("dept_id", m.memberDeptId());
        List<Map<String, Object>> rows = usageEventMapper.selectMaps(qw);
        Map<String, Object> agg = rows.isEmpty() ? Map.of("total", java.math.BigDecimal.ZERO, "cnt", 0L) : rows.get(0);
        var spend = new java.math.BigDecimal(String.valueOf(agg.get("total")));
        return Results.ok(Map.of(
                "period", java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy 年 MM 月")),
                "spentAmount", spend.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
                "eventCount", Integer.parseInt(String.valueOf(agg.get("cnt"))),
                "lastCut", "2026-11-03 00:05"));
    }

    /** 消费明细（事件级；成员仅本部门；未分配部门仅管理员可见） */
    @GetMapping("/usage-events")
    public Result<PageResult<UsageEvent>> usageEvents(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                      @RequestParam(defaultValue = "1") int pageNum,
                                                      @RequestParam(defaultValue = "20") int pageSize,
                                                      @RequestParam(required = false) Long productId,
                                                      @RequestParam(required = false) String startDate,
                                                      @RequestParam(required = false) String endDate) {
        Me m = me(authorization);
        var qw = new LambdaQueryWrapper<UsageEvent>().eq(UsageEvent::getTenantId, m.tenantId());
        if (m.memberDeptId() != null) {
            qw.eq(UsageEvent::getDeptId, m.memberDeptId());     // 部门级数据隔离
        } else if (m.memberDeptId() == null && !keyCacheService.resolveMe(authorization).portal().equals("a")) {
            // 管理员（张伟）deptId=NULL：可见全租户含"未分配"
        }
        if (productId != null) qw.eq(UsageEvent::getProductId, productId);
        if (startDate != null && !startDate.isBlank()) qw.ge(UsageEvent::getArriveTime, startDate + " 00:00:00");
        if (endDate != null && !endDate.isBlank()) qw.le(UsageEvent::getArriveTime, endDate + " 23:59:59");
        Page<UsageEvent> page = usageEventMapper.selectPage(new Page<>(pageNum, pageSize),
                qw.orderByDesc(UsageEvent::getArriveTime));
        // 脱敏：成员角色不回传指纹字段（biz_no）
        page.getRecords().forEach(e -> e.setBizNo(null));
        return Results.ok(new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize));
    }

    /** Key 维度用量统计（本月，按上报 Key 聚合；SQL 聚合规范 #5.9） */
    @GetMapping("/keys/stats")
    public Result<List<Map<String, Object>>> keyStats(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                      @RequestParam(required = false) String period) {
        Me m = me(authorization);
        LocalDate monthStart = period == null || period.isBlank()
                ? LocalDate.now().withDayOfMonth(1)
                : java.time.YearMonth.parse(period).atDay(1);
        var qw = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UsageEvent>()
                .select("key_id", "COUNT(*) AS cnt", "IFNULL(SUM(cost_amount),0) AS cost")
                .eq("tenant_id", m.tenantId())
                .eq("status", "normal")
                .ge("arrive_time", monthStart.atStartOfDay())
                .isNotNull("key_id")
                .groupBy("key_id");
        List<Map<String, Object>> rows = usageEventMapper.selectMaps(qw);
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("keyId", r.get("key_id"));
            row.put("events", Integer.parseInt(String.valueOf(r.get("cnt"))));
            row.put("costCents", com.meterflow.common.utils.Money.toCents(new java.math.BigDecimal(String.valueOf(r.get("cost")))));
            out.add(row);
        }
        return Results.ok(out);
    }

    /** 部门消费统计（本月，按部门聚合；成员仅本部门——部门级数据隔离） */
    @GetMapping("/depts/stats")
    public Result<List<Map<String, Object>>> deptStats(@RequestHeader(value = "Authorization", required = false) String authorization) {
        Me m = me(authorization);
        var qw = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UsageEvent>()
                .select("dept_id", "IFNULL(MAX(dept_name),'未分配') AS dept_name", "COUNT(*) AS cnt", "IFNULL(SUM(cost_amount),0) AS cost")
                .eq("tenant_id", m.tenantId())
                .eq("status", "normal")
                .ge("arrive_time", LocalDate.now().withDayOfMonth(1).atStartOfDay());
        if (m.memberDeptId() != null) qw.eq("dept_id", m.memberDeptId());   // 成员仅本部门
        qw.groupBy("dept_id");
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (Map<String, Object> r : usageEventMapper.selectMaps(qw)) {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("deptId", r.get("dept_id"));
            row.put("deptName", r.get("dept_name"));
            row.put("events", Integer.parseInt(String.valueOf(r.get("cnt"))));
            row.put("costCents", com.meterflow.common.utils.Money.toCents(new java.math.BigDecimal(String.valueOf(r.get("cost")))));
            out.add(row);
        }
        return Results.ok(out);
    }

    /** 资源包实例抵扣留痕（D2 来源明细，R34） */
    @GetMapping("/package-instances/{instanceId}/deductions")
    public Result<PageResult<DeductionItem>> instanceDeductions(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                                @PathVariable Long instanceId,
                                                                @RequestParam(defaultValue = "1") int pageNum,
                                                                @RequestParam(defaultValue = "20") int pageSize) {
        me(authorization);
        Page<DeductionItem> page = deductionItemMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<DeductionItem>()
                        .eq(DeductionItem::getSourceId, instanceId)
                        .in(DeductionItem::getSourceType, "pkg_spec", "pkg_common")
                        .orderByDesc(DeductionItem::getId));
        return Results.ok(new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize));
    }
}
