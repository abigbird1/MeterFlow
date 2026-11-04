package com.meterflow.platform.service;

import cn.dev33.satoken.secure.SaSecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meterflow.platform.entity.ApiKey;
import com.meterflow.common.exception.BusinessException;
import com.meterflow.common.exception.GlobalErrorCode;
import com.meterflow.platform.mapper.ApiKeyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

/** API Key 管理（M2）：hash 存储（ET4）+ 掩码展示 + 轮换并行 24h（需求 4.6） */
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long ROTATE_PARALLEL_HOURS = 24;

    private final ApiKeyMapper apiKeyMapper;
    private final AuditService auditService;

    public List<ApiKey> list(Long tenantId) {
        return apiKeyMapper.selectList(new LambdaQueryWrapper<ApiKey>()
                .eq(ApiKey::getTenantId, tenantId).orderByDesc(ApiKey::getId));
    }

    /** 创建：明文一次性返回，库中仅存 SHA-256（ET4） */
    public ApiKey create(Long tenantId, String name, String note, Long deptId, List<String> products) {
        String plain = generateKey();
        ApiKey k = new ApiKey();
        k.setTenantId(tenantId);
        k.setName(name);
        k.setNote(note);
        k.setKeyHash(SaSecureUtil.sha256(plain));
        k.setKeyMask(mask(plain));
        k.setDeptId(deptId);
        // JSON 列：必须写入合法 JSON 数组（如 ["Embedding API"]）
        k.setProducts(products == null || products.isEmpty() ? null :
                products.stream().map(p -> "\"" + p + "\"").collect(java.util.stream.Collectors.joining(",", "[", "]")));
        k.setStatus("enabled");
        apiKeyMapper.insert(k);
        auditService.record("创建 API Key", name, note == null ? "—" : note, true);
        k.setPlainKey(plain); // 明文一次性返回（不落库不缓存，ET4）
        return k;
    }

    /** 启用/禁用（敏感操作，审计留痕） */
    public void changeStatus(Long tenantId, Long id, String action) {
        ApiKey k = require(tenantId, id);
        k.setStatus("enable".equals(action) ? "enabled" : "disabled");
        apiKeyMapper.updateById(k);
        auditService.record(("enable".equals(action) ? "启用" : "禁用") + " API Key", k.getName(), "—", true);
    }

    /** 轮换：生成新 Key（明文一次性返回），旧 Key 置 rotating 并行 24h 后失效 */
    public ApiKey rotate(Long tenantId, Long id) {
        ApiKey old = require(tenantId, id);
        String plain = generateKey();
        ApiKey nk = new ApiKey();
        nk.setTenantId(tenantId);
        nk.setName(old.getName() + "-new");
        nk.setNote("轮换生成（替换 " + old.getName() + "）");
        nk.setKeyHash(SaSecureUtil.sha256(plain));
        nk.setKeyMask(mask(plain));
        nk.setDeptId(old.getDeptId());
        nk.setProducts(old.getProducts());
        nk.setStatus("enabled");
        apiKeyMapper.insert(nk);

        old.setStatus("rotating");
        old.setRotateDeadline(LocalDateTime.now().plusHours(ROTATE_PARALLEL_HOURS));
        apiKeyMapper.updateById(old);
        auditService.record("API Key 轮换", old.getName(), "新旧 Key 并行 24h", true);
        nk.setPlainKey(plain);
        return nk;
    }

    private ApiKey require(Long tenantId, Long id) {
        ApiKey k = apiKeyMapper.selectById(id);
        if (k == null || !k.getTenantId().equals(tenantId)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "Key 不存在或无权操作");
        }
        return k;
    }

    private String generateKey() {
        byte[] bytes = new byte[14];
        RANDOM.nextBytes(bytes);
        String hex = HexFormat.of().formatHex(bytes);
        return "mfk-" + hex.substring(0, 4) + "-" + hex.substring(4, 8) + "-"
                + hex.substring(8, 12) + "-" + hex.substring(12);
    }

    private String mask(String plain) {
        return plain.substring(0, 8) + "-****-****-" + plain.substring(plain.length() - 4);
    }

    /** 按 hash 精确匹配（/feign/api-keys/{keyHash}，供 billing ingest 校验） */
    public ApiKey byHash(String keyHash) {
        return apiKeyMapper.selectOne(new LambdaQueryWrapper<ApiKey>()
                .eq(ApiKey::getKeyHash, keyHash).last("limit 1"));
    }
}
