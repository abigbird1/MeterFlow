package com.meterflow.billing.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

/** platform 服务 Feign 客户端（身份解析 + Key 上下文；本地直连 URL，Nacos 启用后切服务名） */
@FeignClient(name = "meterflow-platform", contextId = "platformClient")
public interface PlatformClient {

    /** Bearer 透传解析当前身份（/auth/me） */
    @GetMapping("/auth/me")
    Map<String, Object> me(@RequestHeader("Authorization") String authorization);

    /** 按 Key 散列取接入上下文（/feign/api-keys/{keyHash}） */
    @GetMapping("/feign/api-keys/{keyHash}")
    Map<String, Object> apiKeyByHash(@PathVariable("keyHash") String keyHash);

    /** 站内通知落库（预算告警等经 platform /feign/notify） */
    @org.springframework.web.bind.annotation.PostMapping("/feign/notify")
    Map<String, Object> notify(@org.springframework.web.bind.annotation.RequestBody Map<String, Object> payload);
}
