package com.meterflow.billing.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meterflow.billing.entity.BalanceAccount;
import com.meterflow.billing.entity.PackageInstance;
import com.meterflow.billing.entity.UsageEvent;
import com.meterflow.billing.mapper.BalanceAccountMapper;
import com.meterflow.billing.mapper.PackageInstanceMapper;
import com.meterflow.billing.mapper.UsageEventMapper;
import com.meterflow.billing.feign.EquityClient;
import com.meterflow.common.api.Results;
import com.meterflow.common.api.Results.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.Map;

/** 服务间内部接口（05 §7.1 扩展：S4 实例发放回调 / 欠费清单 / 月度汇总 / 调整单生效回写） */
@RestController
@RequestMapping("/feign/billing")
@RequiredArgsConstructor
public class FeignController {

    private final PackageInstanceMapper packageMapper;
    private final BalanceAccountMapper accountMapper;
    private final UsageEventMapper usageEventMapper;
    private final com.meterflow.billing.mapper.DeductionRecordMapper recordMapper;
    private final com.meterflow.billing.mapper.AccountTransactionMapper transactionMapper;
    private final EquityClient equityClient;
    private final com.meterflow.billing.service.BudgetService budgetService;
    private final com.meterflow.billing.service.AnomalyService anomalyService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();



    /** 实例发放（S4 方案 B：事件只传 ID，标题/天数回查 equity；幂等键=orderNo） */
    @PostMapping("/package-instances/grant")
    @Transactional
    public Result<Map<String, Object>> grant(@RequestBody Map<String, String> body) throws Exception {
        Map<String, Object> req = new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(body.get("payload"), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        String orderNo = String.valueOf(req.get("orderNo"));
        Long exists = packageMapper.selectCount(new LambdaQueryWrapper<PackageInstance>()
                .eq(PackageInstance::getSourceOrderNo, orderNo));
        if (exists > 0) return Results.ok(Map.of("granted", false, "reason", "已发放（幂等跳过）"));
        Long goodsId = Long.valueOf(String.valueOf(req.get("goodsId")));
        @SuppressWarnings("unchecked") Map<String, Object> goods = (Map<String, Object>) equityClient.goods(goodsId).get("data");
        PackageInstance p = new PackageInstance();
        p.setTenantId(Long.valueOf(String.valueOf(req.get("tenantId")))); p.setPkgType("spec");
        p.setTitle(goods == null ? "资源包" : String.valueOf(goods.get("name")));
        p.setProductId(Long.valueOf(String.valueOf(goods == null ? req.get("productId") : goods.get("productId"))));
        p.setMetricKey(String.valueOf(goods.get("metricKey")));
        p.setTotal(new BigDecimal(String.valueOf(goods.get("quantity"))));
        p.setRemaining(p.getTotal()); p.setUnit(String.valueOf(goods.get("unit")));
        p.setExpireDate(LocalDate.now().plusDays(Long.parseLong(String.valueOf(goods.get("validDays")))));
        p.setStatus("active"); p.setSourceOrderNo(orderNo);
        packageMapper.insert(p);
        return Results.ok(Map.of("granted", true, "instanceId", p.getId()));
    }

    /** goods 只读详情（billing 回查，方案 B） */
    @GetMapping("/feign/goods/{goodsId}")
    public Result<Map<String, Object>> goods(@PathVariable Long goodsId) {
        return Results.fail("PLT-0404", "由 equity 提供（占位：Feign 直连 equity 已在 03 拓扑声明）");
    }

    /** 实例台账列表（equity 我的资源包 / platform 租户详情） */
    @GetMapping("/instances/{tenantId}")
    public Result<List<PackageInstance>> instances(@PathVariable Long tenantId) {
        return Results.ok(packageMapper.selectList(new LambdaQueryWrapper<PackageInstance>()
                .eq(PackageInstance::getTenantId, tenantId).orderByDesc(PackageInstance::getId)));
    }


    /** 日汇总（A1 日切快照依据：按到达日聚合事件数与费用） */
    @GetMapping("/daily-summary/{tenantId}/{date}")
    public Result<Map<String, Object>> dailySummary(@PathVariable Long tenantId, @PathVariable String date) {
        LocalDate d = LocalDate.parse(date);
        List<UsageEvent> events = usageEventMapper.selectList(new LambdaQueryWrapper<UsageEvent>()
                .eq(UsageEvent::getTenantId, tenantId)
                .ge(UsageEvent::getArriveTime, d.atStartOfDay())
                .lt(UsageEvent::getArriveTime, d.plusDays(1).atStartOfDay()));
        BigDecimal cost = events.stream().map(UsageEvent::getCostAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return Results.ok(Map.of("tenantId", tenantId, "date", date,
                "eventCount", events.size(), "costCents", com.meterflow.common.utils.Money.toCents(cost)));
    }

    /** 部门维度月汇总（A2 出账部门小计依据） */
    @GetMapping("/month-dept-sub/{tenantId}/{period}")
    public Result<List<Map<String, Object>>> monthDeptSub(@PathVariable Long tenantId, @PathVariable String period) {
        LocalDateTime start = java.time.YearMonth.parse(period).atDay(1).atStartOfDay();
        List<UsageEvent> events = usageEventMapper.selectList(new LambdaQueryWrapper<UsageEvent>()
                .eq(UsageEvent::getTenantId, tenantId)
                .ge(UsageEvent::getArriveTime, start)
                .lt(UsageEvent::getArriveTime, start.plusMonths(1)));
        Map<String, BigDecimal> byDept = new java.util.LinkedHashMap<>();
        for (UsageEvent e : events) {
            String dept = e.getDeptName() == null ? "未分配" : e.getDeptName();
            byDept.merge(dept, e.getCostAmount(), BigDecimal::add);
        }
        List<Map<String, Object>> rows = byDept.entrySet().stream()
                .map(en -> Map.<String, Object>of("deptName", en.getKey(),
                        "amountCents", com.meterflow.common.utils.Money.toCents(en.getValue())))
                .collect(java.util.stream.Collectors.toList());
        return Results.ok(rows);
    }

    /** 全部账户（platform 租户列表聚合） */
    @GetMapping("/accounts")
    public Result<List<BalanceAccount>> accounts() {
        return Results.ok(accountMapper.selectList(null));
    }

    /** 欠费账户清单（finance 催缴/账单联动） */
    @GetMapping("/accounts/debts")
    public Result<List<BalanceAccount>> debts() {
        return Results.ok(accountMapper.selectList(new LambdaQueryWrapper<BalanceAccount>()
                .lt(BalanceAccount::getBalanceCents, 0L)));
    }

    /** 单账户（platform 租户详情展示） */
    @GetMapping("/accounts/{tenantId}")
    public Result<BalanceAccount> account(@PathVariable Long tenantId) {
        return Results.ok(accountMapper.selectOne(new LambdaQueryWrapper<BalanceAccount>()
                .eq(BalanceAccount::getTenantId, tenantId).last("limit 1")));
    }

    /** 预算告警手动触发（正常由每小时 @Scheduled 驱动；返回本次发送条数） */
    @PostMapping("/budget-check")
    public Result<Integer> budgetCheck() {
        return Results.ok(budgetService.checkAndNotify());
    }

    /** 消费异常检测手动触发（正常由每日 00:15 @Scheduled 驱动；date 缺省为昨日） */
    @PostMapping("/anomaly-check")
    public Result<Integer> anomalyCheck(@RequestParam(required = false) String date) {
        return Results.ok(anomalyService.scan(date == null || date.isBlank()
                ? java.time.LocalDate.now().minusDays(1)
                : java.time.LocalDate.parse(date)));
    }

    /** 三口径对账数据束（R26，finance 比对用）：A 计量(priced 事件) / B 计费(抵扣流水含钱包部分) / C 账户(余额扣减) */
    @GetMapping("/recon-bundle/{date}")
    public Result<Map<String, Object>> reconBundle(@PathVariable String date,
                                                   @RequestParam(required = false) String endDate) {
        LocalDate d = LocalDate.parse(date);
        LocalDateTime start = d.atStartOfDay();
        // endDate 缺省为单日（日切对账）；传入则为区间（月末全量复核 R26）
        LocalDateTime end = (endDate == null || endDate.isBlank())
                ? start.plusDays(1)
                : LocalDate.parse(endDate).plusDays(1).atStartOfDay();
        // A：当日受理并计价的事件（status=normal 即计价成功入账；rejected/errored 不参与对账）
        List<UsageEvent> events = usageEventMapper.selectList(new LambdaQueryWrapper<UsageEvent>()
                .ge(UsageEvent::getArriveTime, start).lt(UsageEvent::getArriveTime, end)
                .eq(UsageEvent::getStatus, "normal"));
        // B：当日计费流水（chain_json 预解析出钱包抵扣金额）
        List<com.meterflow.billing.entity.DeductionRecord> records = recordMapper.selectList(
                new LambdaQueryWrapper<com.meterflow.billing.entity.DeductionRecord>()
                        .ge(com.meterflow.billing.entity.DeductionRecord::getCreatedAt, start)
                        .lt(com.meterflow.billing.entity.DeductionRecord::getCreatedAt, end));
        // C：当日余额扣减流水（按事件聚合）
        List<com.meterflow.billing.entity.AccountTransaction> txs = transactionMapper.selectList(
                new LambdaQueryWrapper<com.meterflow.billing.entity.AccountTransaction>()
                        .eq(com.meterflow.billing.entity.AccountTransaction::getTxType, "deduct")
                        .eq(com.meterflow.billing.entity.AccountTransaction::getRefType, "usage_event")
                        .ge(com.meterflow.billing.entity.AccountTransaction::getCreatedAt, start)
                        .lt(com.meterflow.billing.entity.AccountTransaction::getCreatedAt, end));
        Map<Long, Long> deductByEvent = new java.util.HashMap<>();
        for (var tx : txs) deductByEvent.merge(tx.getRefId(), -tx.getAmountCents(), Long::sum);
        return Results.ok(Map.of(
                "date", date,
                "endDate", endDate == null ? "" : endDate,
                "events", events,
                "records", records.stream().map(r -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("eventId", r.getEventId());
                    m.put("tenantId", r.getTenantId());
                    m.put("costCents", com.meterflow.common.utils.Money.toCents(r.getCostAmount()));
                    m.put("walletCents", walletCents(r.getChainJson()));
                    return m;
                }).toList(),
                "deducts", deductByEvent.entrySet().stream().map(e -> Map.of("eventId", e.getKey(), "walletCents", e.getValue())).toList()));
    }

    /** chain_json 中 ④余额 步骤金额合计（分）；解析失败按 0（对账侧会视为账户不平暴露） */
    private long walletCents(String chainJson) {
        try {
            long sum = 0;
            for (var step : objectMapper.readValue(chainJson, new com.fasterxml.jackson.core.type.TypeReference<java.util.List<Map<String, Object>>>() { })) {
                Object src = step.get("source");
                if (src != null && String.valueOf(src).contains("余额")) {
                    sum += com.meterflow.common.utils.Money.toCents(new java.math.BigDecimal(String.valueOf(step.get("amount"))));
                }
            }
            return sum;
        } catch (Exception e) {
            return Long.MIN_VALUE; // 解析失败哨兵：上游按账户不平暴露
        }
    }

    /** 指定账期有用量事件的租户清单（finance 月末全员出账 R17 用） */
    @GetMapping("/active-tenants/{period}")
    public Result<List<Long>> activeTenants(@PathVariable String period) {
        LocalDateTime start = java.time.YearMonth.parse(period).atDay(1).atStartOfDay();
        LocalDateTime end = start.plusMonths(1);
        List<UsageEvent> events = usageEventMapper.selectList(new LambdaQueryWrapper<UsageEvent>()
                .ge(UsageEvent::getArriveTime, start).lt(UsageEvent::getArriveTime, end)
                .select(UsageEvent::getTenantId));
        return Results.ok(events.stream().map(UsageEvent::getTenantId).distinct().toList());
    }

    /** 平台近 N 日消费趋势（全租户日聚合；platform 数据看板用） */
    @GetMapping("/platform-trend")
    public Result<List<Map<String, Object>>> platformTrend(@RequestParam(defaultValue = "7") int days) {
        int safeDays = Math.max(1, Math.min(days, 30));
        LocalDateTime start = java.time.LocalDate.now().minusDays(safeDays - 1L).atStartOfDay();
        // SQL 按日聚合（规范 #5.9），再补齐空日期
        var qw = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UsageEvent>()
                .select("DATE(arrive_time) AS d", "SUM(cost_amount) AS c", "COUNT(*) AS n")
                .ge("arrive_time", start)
                .groupBy("DATE(arrive_time)");
        Map<String, Map<String, Object>> byDay = new java.util.HashMap<>();
        for (Map<String, Object> r : usageEventMapper.selectMaps(qw)) byDay.put(String.valueOf(r.get("d")), r);
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (int i = safeDays - 1; i >= 0; i--) {
            LocalDate d = java.time.LocalDate.now().minusDays(i);
            Map<String, Object> r = byDay.get(d.toString());
            BigDecimal cost = r == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(r.get("c")));
            long n = r == null ? 0L : Long.parseLong(String.valueOf(r.get("n")));
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("date", d.toString());
            row.put("eventCount", (int) n);
            row.put("costCents", com.meterflow.common.utils.Money.toCents(cost));
            out.add(row);
        }
        return Results.ok(out);
    }

    /** 月度汇总（finance 月末出账 R17 依据） */
    @GetMapping("/month-summary/{tenantId}/{period}")
    public Result<Map<String, Object>> monthSummary(@PathVariable Long tenantId, @PathVariable String period) {
        LocalDateTime start = java.time.YearMonth.parse(period).atDay(1).atStartOfDay();
        LocalDateTime end = start.plusMonths(1);
        // SQL 聚合代替全量实体加载（规范 #5.9）
        var qw = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UsageEvent>()
                .select("IFNULL(SUM(cost_amount),0) AS total", "COUNT(*) AS cnt")
                .eq("tenant_id", tenantId)
                .ge("arrive_time", start).lt("arrive_time", end);
        List<Map<String, Object>> rows = usageEventMapper.selectMaps(qw);
        Map<String, Object> agg = rows.isEmpty() ? Map.of("total", BigDecimal.ZERO, "cnt", 0L) : rows.get(0);
        BigDecimal cost = new BigDecimal(String.valueOf(agg.get("total")));
        return Results.ok(Map.of(
                "tenantId", tenantId, "period", period,
                "eventCount", Integer.parseInt(String.valueOf(agg.get("cnt"))),
                "costCents", com.meterflow.common.utils.Money.toCents(cost)));
    }

    public record AdjustRequest(Long tenantId, Long amountCents, String refType, String refId, String note) {}

    /** 调整单生效回写余额（红冲/蓝补，finance 审批通过后回调；P3 资金入口之一） */
    @PostMapping("/balance-adjust")
    @Transactional
    public Result<Map<String, Object>> adjust(@RequestBody AdjustRequest req) {
        BalanceAccount acc = accountMapper.selectOne(new LambdaQueryWrapper<BalanceAccount>()
                .eq(BalanceAccount::getTenantId, req.tenantId()).last("FOR UPDATE"));
        if (acc == null) return Results.fail("BIL-0404", "余额账户不存在");
        acc.setBalanceCents(acc.getBalanceCents() + req.amountCents());
        accountMapper.updateById(acc);
        // 入账后停服判定（R22/R23 回正恢复）
        String stage = acc.getStatus();
        long b = acc.getBalanceCents();
        if (b >= 0 && !"normal".equals(stage)) {
            stage = "normal"; acc.setStatus(stage);
        } else if (b < 0 && "normal".equals(stage)) {
            stage = "warning"; acc.setStatus(stage);
        }
        if (b <= acc.getOverdraftCents()) stage = "suspended";
        acc.setStatus(stage);
        accountMapper.updateById(acc);
        return Results.ok(Map.of("balanceCents", acc.getBalanceCents(), "stage", stage));
    }
}
