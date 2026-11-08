package com.meterflow.billing.controller;

import com.meterflow.billing.service.BudgetService;
import com.meterflow.billing.service.KeyCacheService;
import com.meterflow.common.api.Results;
import com.meterflow.common.api.Results.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 管理端预算告警清单（运营监控驾驶舱数据源）：当期触发预警/用尽阈值的租户清单 */
@RestController
@RequestMapping("/a/budget-alerts")
@RequiredArgsConstructor
public class AdminBudgetController {

    private final BudgetService budgetService;
    private final KeyCacheService keyCacheService;

    @GetMapping
    public Result<List<Map<String, Object>>> alerts(@org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authorization) {
        KeyCacheService.MeContext me = keyCacheService.resolveMe(authorization);
        if (me == null || !"a".equals(me.portal())) {
            throw new com.meterflow.common.exception.BusinessException("GWY-0401", "登录已失效，请重新登录");
        }
        return Results.ok(budgetService.adminAlerts());
    }
}
