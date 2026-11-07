package com.meterflow.platform.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/** billing 内部只读（租户列表余额聚合） */
@FeignClient(name = "meterflow-billing", contextId = "billingClient")
public interface BillingClient {

    @GetMapping("/feign/billing/accounts/{tenantId}")
    Map<String, Object> account(@PathVariable("tenantId") Long tenantId);

    /** 平台近 N 日消费趋势（数据看板）；billing 不可用时调用方降级 */
    @GetMapping("/feign/billing/platform-trend")
    Map<String, Object> platformTrend(@org.springframework.web.bind.annotation.RequestParam("days") int days);
}
