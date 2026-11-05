package com.meterflow.finance.controller;

import com.meterflow.common.api.Results;
import com.meterflow.common.api.Results.Result;
import com.meterflow.finance.entity.UrgeRecord;
import com.meterflow.finance.feign.BillingClient;
import com.meterflow.finance.mapper.UrgeRecordMapper;
import com.meterflow.finance.service.IdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 欠费与催缴（05 §6.3.6/6.3.7）：清单来自 billing Feign；催缴留痕 */
@RestController
@RequestMapping("/a/debts")
@RequiredArgsConstructor
public class DebtController {

    private final BillingClient billingClient;
    private final IdentityService identityService;
    private final UrgeRecordMapper urgeMapper;
    private final org.springframework.web.client.RestTemplate platformNotify = new org.springframework.web.client.RestTemplate();

    /** D6：催缴站内通知触达（platform /feign/notify） */
    private void platformNotify(Long tenantId, String title, String content) {
        try {
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("tenantId", tenantId); payload.put("type", "warn");
            payload.put("title", title); payload.put("content", content);
            platformNotify.postForEntity("http://localhost:8081/feign/notify", payload, Void.class);
        } catch (Exception ignored) { }
    }

    @GetMapping
    @SuppressWarnings("unchecked")
    public Result<List<Map<String, Object>>> list(@RequestHeader("Authorization") String authorization) {
        identityService.require(authorization);
        Map<String, Object> resp = billingClient.debts();
        return Results.ok((List<Map<String, Object>>) resp.get("data"));
    }

    @PostMapping("/{tenantId}/urge")
    @SuppressWarnings("unchecked")
    public Result<UrgeRecord> urge(@RequestHeader("Authorization") String authorization,
                                   @PathVariable Long tenantId) {
        identityService.require(authorization);
        Map<String, Object> resp = billingClient.account(tenantId);
        Map<String, Object> acc = (Map<String, Object>) resp.get("data");
        long balance = acc == null || acc.get("balanceCents") == null
                ? 0 : Long.parseLong(String.valueOf(acc.get("balanceCents")));
        platformNotify(tenantId, "催缴通知", "欠费 " + balance / 100.0 + " 元，请尽快充值恢复服务（R25）");
        UrgeRecord u = new UrgeRecord();
        u.setTenantId(tenantId);
        u.setBalanceCents(java.math.BigDecimal.valueOf(balance));
        u.setDetail("欠费 " + balance / 100.0 + " 元，请尽快充值恢复服务（R25）");
        u.setCreatedAt(LocalDateTime.now());
        urgeMapper.insert(u);
        return Results.ok(u);
    }
}
