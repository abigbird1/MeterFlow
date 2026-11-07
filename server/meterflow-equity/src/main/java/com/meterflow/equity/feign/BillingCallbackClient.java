package com.meterflow.equity.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;
import java.util.List;

/** billing 回调客户端（S4：outbox relay 投递"订单已支付"→ 发放实例；MQ 接入后替换投递通道） */
@FeignClient(name = "meterflow-billing", contextId = "billingCallbackClient")
public interface BillingCallbackClient {

    @PostMapping("/feign/billing/package-instances/grant")
    Map<String, Object> grantInstance(@RequestBody Map<String, Object> payload);

    /** 实例台账只读（我的资源包，S4 余量归 billing） */
    @GetMapping("/feign/billing/instances/{tenantId}")
    Map<String, Object> instances(@PathVariable("tenantId") Long tenantId);
}
