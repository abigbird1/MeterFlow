package com.meterflow.billing.controller;

import com.meterflow.billing.entity.RechargeRecord;
import com.meterflow.billing.service.BalanceService;
import com.meterflow.billing.service.KeyCacheService;
import com.meterflow.billing.service.KeyCacheService.MeContext;
import com.meterflow.common.api.Results;
import com.meterflow.common.api.Results.Result;
import com.meterflow.common.exception.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 余额与充值（05 §5.2）：模拟支付即时到账；回正即时恢复（R23） */
@RestController
@RequestMapping("/t/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final KeyCacheService keyCacheService;
    private final BalanceService balanceService;
    private final java.util.Map<String, com.meterflow.common.api.Results.Result<java.util.Map<String, Object>>> idemCache = new java.util.concurrent.ConcurrentHashMap<>();

    public record RechargeRequest(@NotNull Long amountCents, String method) {}

    private MeContext me(String authorization) {
        MeContext me = keyCacheService.resolveMe(authorization);
        if (me == null) throw new BusinessException("GWY-0401", "登录已失效，请重新登录");
        return me;
    }

    @GetMapping
    public Result<Map<String, Object>> wallet(@RequestHeader(value = "Authorization", required = false) String authorization) {
        MeContext me = me(authorization);
        var acc = balanceService.wallet(me.tenantId());
        return Results.ok(Map.of(
                "balanceCents", acc.getBalanceCents(),
                "overdraftCents", acc.getOverdraftCents(),
                "stage", acc.getStatus(),
                "tenantName", me.name()));
    }

    @GetMapping("/recharges")
    public Result<List<RechargeRecord>> recharges(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return Results.ok(balanceService.recharges(me(authorization).tenantId()));
    }

    @PostMapping("/recharge")
    public Result<Map<String, Object>> recharge(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                @RequestHeader(value = "Idempotency-Key", required = false) String idemKey,
                                                @Valid @RequestBody RechargeRequest req) {
        MeContext me = me(authorization);
        if (idemKey != null && !idemKey.isBlank()) {
            var hit = idemCache.get(idemKey);          // E3：幂等重放返回首次结果
            if (hit != null) return hit;
        }
        var r = balanceService.recharge(me.tenantId(), req.amountCents(),
                req.method() == null ? "支付宝（模拟）" : req.method());
        Result<Map<String, Object>> resp = Results.ok(Map.of("balanceCents", r.balanceCents(), "stage", r.stage(), "recovered", r.recovered()));
        if (idemKey != null && !idemKey.isBlank()) idemCache.put(idemKey, resp);
        return resp;
    }
}
