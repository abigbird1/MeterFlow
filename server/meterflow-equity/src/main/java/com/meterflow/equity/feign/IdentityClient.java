package com.meterflow.equity.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

/** 身份解析（Bearer 透传 /auth/me） */
@FeignClient(name = "meterflow-platform-auth", url = "${meterflow.platform-url:http://localhost:8081}")
public interface IdentityClient {

    @GetMapping("/auth/me")
    Map<String, Object> me(@RequestHeader("Authorization") String authorization);
}
