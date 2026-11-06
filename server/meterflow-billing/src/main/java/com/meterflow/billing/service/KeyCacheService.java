package com.meterflow.billing.service;

import com.meterflow.billing.feign.PlatformClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Key 上下文缓存（05 §7.1：Redis 为主路径，骨架阶段以进程内 30s TTL 缓存替代，生产替换为 Redis）。
 * 事件上报 X-API-Key → SHA-256 → 缓存/Feign → 租户/部门/产品集。
 */
@Service
@RequiredArgsConstructor
public class KeyCacheService {

    private static final long TTL_MS = 30_000;

    private final PlatformClient platformClient;
    private final Map<String, CachedKey> cache = new ConcurrentHashMap<>();

    public record KeyContext(Long keyId, Long tenantId, Long deptId, boolean deptAssigned,
                             String deptName, String name, String status,
                             java.util.List<String> products) {}

    public KeyContext resolve(String apiKey) {
        String hash = sha256(apiKey);
        CachedKey hit = cache.get(hash);
        if (hit != null && System.currentTimeMillis() - hit.at < TTL_MS) return hit.ctx;
        Map<String, Object> resp = platformClient.apiKeyByHash(hash);
        Map<String, Object> data = cast(resp.get("data"));
        if (data == null || "PLT-0404".equals(String.valueOf(resp.get("code")))) return null;
        KeyContext ctx = new KeyContext(
                Long.valueOf(String.valueOf(data.get("keyId"))),
                Long.valueOf(String.valueOf(data.get("tenantId"))),
                Long.valueOf(String.valueOf(data.get("deptId"))),
                Boolean.parseBoolean(String.valueOf(data.get("deptAssigned"))),
                String.valueOf(data.get("deptName")),
                String.valueOf(data.get("name")),
                String.valueOf(data.get("status")),
                (java.util.List<String>) data.get("products"));
        cache.put(hash, new CachedKey(System.currentTimeMillis(), ctx));
        return ctx;
    }

    /** 身份解析（Bearer 透传 /auth/me） */
    public MeContext resolveMe(String authorization) {
        Map<String, Object> resp;
        try {
            resp = platformClient.me(authorization);
        } catch (Exception fe) {
            return null;   // 未登录/平台不可达 → 统一按未登录处理（401 由异常映射层输出）
        }
        Map<String, Object> data = cast(resp.get("data"));
        if (data == null) return null;
        Long tenantId = data.get("tenantId") == null ? null : Long.valueOf(String.valueOf(data.get("tenantId")));
        Long deptId = data.get("deptId") == null ? null : Long.valueOf(String.valueOf(data.get("deptId")));
        return new MeContext(Long.valueOf(String.valueOf(data.get("id"))),
                String.valueOf(data.get("name")), String.valueOf(data.get("portal")), tenantId, deptId,
                String.valueOf(data.get("role")));
    }

    public record MeContext(Long userId, String name, String portal, Long tenantId, Long deptId, String role) {}

    private record CachedKey(long at, KeyContext ctx) {}

    @SuppressWarnings("unchecked")
    private Map<String, Object> cast(Object o) { return (Map<String, Object>) o; }

    /** SHA-256 hex（与 platform ET4 口径一致） */
    public static String sha256(String raw) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
