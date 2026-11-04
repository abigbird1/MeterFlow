package com.meterflow.equity.service;

import com.meterflow.equity.feign.IdentityClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

/** 身份解析：Bearer → {userId, tenantId}；解析失败视为未登录 */
@Service
@RequiredArgsConstructor
public class IdentityService {

    private final IdentityClient identityClient;

    public record Me(Long userId, String portal, Long tenantId) {}

    public Me requireTenant(String authorization) {
        Map<String, Object> resp = identityClient.me(authorization);
        Map<String, Object> data = cast(resp.get("data"));
        if (data == null || !"t".equals(String.valueOf(data.get("portal")))) return null;
        Long tenantId = data.get("tenantId") == null ? null : Long.valueOf(String.valueOf(data.get("tenantId")));
        Long userId = data.get("id") == null ? null : Long.valueOf(String.valueOf(data.get("id")));
        return new Me(userId, "t", tenantId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> cast(Object o) { return (Map<String, Object>) o; }
}
