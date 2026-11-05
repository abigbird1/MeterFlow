package com.meterflow.finance.service;

import com.meterflow.finance.feign.IdentityClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

/** 身份解析：Bearer → {userId, role}；解析失败视为未登录 */
@Service
@RequiredArgsConstructor
public class IdentityService {

    private final IdentityClient identityClient;

    public record Me(Long userId, String name, String role, Long tenantId) {}

    public Me require(String authorization) {
        Map<String, Object> resp = identityClient.me(authorization);
        Map<String, Object> data = cast(resp.get("data"));
        if (data == null) return null;
        Long id = data.get("id") == null ? null : Long.valueOf(String.valueOf(data.get("id")));
        Long tenantId = data.get("tenantId") == null ? null : Long.valueOf(String.valueOf(data.get("tenantId")));
        return new Me(id, String.valueOf(data.get("name")), String.valueOf(data.get("role")), tenantId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> cast(Object o) { return (Map<String, Object>) o; }
}
