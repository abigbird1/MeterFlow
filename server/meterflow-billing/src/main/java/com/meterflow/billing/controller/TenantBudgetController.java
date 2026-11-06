package com.meterflow.billing.controller;

import com.meterflow.billing.service.BudgetService;
import com.meterflow.billing.service.KeyCacheService;
import com.meterflow.billing.service.KeyCacheService.MeContext;
import com.meterflow.common.api.Results;
import com.meterflow.common.api.Results.Result;
import com.meterflow.common.exception.BusinessException;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 租户月度预算（新功能）：查看/设置；设置仅租户管理员（4.3 余额域扩展） */
@RestController
@RequestMapping("/t/budget")
@RequiredArgsConstructor
public class TenantBudgetController {

    private final BudgetService budgetService;
    private final KeyCacheService keyCacheService;

    private MeContext me(String authorization) {
        MeContext me = keyCacheService.resolveMe(authorization);
        if (me == null || !"t".equals(me.portal())) {
            throw new BusinessException(new com.meterflow.common.exception.ErrorCode() {
                @Override public String getCode() { return "GWY-0401"; }
                @Override public String getMessage() { return "登录已失效，请重新登录"; }
            });
        }
        return me;
    }

    @GetMapping
    public Result<Map<String, Object>> status(@RequestHeader(value = "Authorization", required = false) String authorization) {
        MeContext me = me(authorization);
        return Results.ok(budgetService.status(me.tenantId()));
    }

    public record SetRequest(@NotNull Long budgetCents) {}

    @PostMapping
    public Result<Map<String, Object>> set(@RequestHeader(value = "Authorization", required = false) String authorization,
                                           @RequestBody SetRequest req) {
        MeContext me = me(authorization);
        if (!"管理员".equals(me.role()) && !"sadmin".equals(me.role())) {
            throw new BusinessException("GWY-0403", "仅租户管理员可设置预算");
        }
        if (req.budgetCents() == null || req.budgetCents() <= 0 || req.budgetCents() > 10_000_000_00L) {
            throw new BusinessException("BIL-4005", "预算需在 0.01 元 ~ 1000 万元之间");
        }
        return Results.ok(budgetService.upsert(me.tenantId(), req.budgetCents()));
    }
}
