package com.meterflow.finance.controller;

import com.meterflow.common.api.Results;
import com.meterflow.common.api.Results.Result;
import com.meterflow.finance.entity.Bill;
import com.meterflow.finance.service.BillService;
import com.meterflow.finance.service.IdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 租户端账单（05 §5.4.1/5.4.3）：数据域在 finance，网关路由 /t/bills/** → 8084 */
@RestController
@RequestMapping("/t/bills")
@RequiredArgsConstructor
public class TenantBillController {

    private final BillService billService;
    private final IdentityService identityService;

    private Long tenant(String authorization) {
        var me = identityService.require(authorization);
        if (me == null) throw new com.meterflow.common.exception.BusinessException("GWY-0401", "登录已失效，请重新登录");
        return me.tenantId();
    }

    @GetMapping
    public Result<List<Bill>> list(@RequestHeader("Authorization") String authorization) {
        return Results.ok(billService.byTenant(tenant(authorization)));
    }

    @GetMapping("/{billNo}")
    public Result<Bill> detail(@RequestHeader("Authorization") String authorization,
                               @PathVariable String billNo) {
        return Results.ok(billService.detail(billNo));
    }

    /** 账单详情聚合：费用构成（原额+调整=应付）+ 版本分段 + 部门小计 */
    @GetMapping("/{billNo}/detail")
    public Result<Map<String, Object>> detailBundle(@RequestHeader("Authorization") String authorization,
                                                    @PathVariable String billNo) {
        tenant(authorization);
        return Results.ok(billService.detailBundle(billNo));
    }

    /** 本账期进度（日切定案前的实时消费，05 §5.4.1）：金额按规范以分（*_cents）出参 */
    @GetMapping("/progress")
    public Result<Map<String, Object>> progress(@RequestHeader("Authorization") String authorization) {
        Long tenantId = tenant(authorization);
        String period = java.time.YearMonth.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
        Map<String, Object> summary = billService.monthSummary(tenantId, period);
        return Results.ok(Map.of(
                "period", period,
                "spentAmountCents", summary.get("costCents") == null ? 0L : Long.valueOf(String.valueOf(summary.get("costCents"))),
                "eventCount", summary.get("eventCount") == null ? 0 : Integer.valueOf(String.valueOf(summary.get("eventCount")))));
    }
}
