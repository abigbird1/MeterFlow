package com.meterflow.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meterflow.common.api.Results;
import com.meterflow.common.api.Results.Result;
import com.meterflow.platform.entity.ApiKey;
import com.meterflow.platform.mapper.DepartmentMapper;
import com.meterflow.platform.service.ApiKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 服务间内部接口（05 §7.1，OpenFeign；内网调用，不走用户权限点） */
@RestController
@RequestMapping("/feign")
@RequiredArgsConstructor
public class FeignController {

    private final ApiKeyService apiKeyService;
    private final DepartmentMapper departmentMapper;
    private final com.meterflow.platform.mapper.NotificationMapper notificationMapper;

    /** 站内通知落库（A3/D6：finance 事件联动触达租户） */
    @PostMapping("/notify")
    public Result<com.meterflow.platform.entity.Notification> notify(@RequestBody Map<String, Object> body) {
        Long tenantId = Long.valueOf(String.valueOf(body.get("tenantId")));
        com.meterflow.platform.entity.Notification n = new com.meterflow.platform.entity.Notification();
        n.setTenantId(tenantId);
        n.setType(String.valueOf(body.getOrDefault("type", "info")));
        n.setTitle(String.valueOf(body.getOrDefault("title", "平台通知")));
        n.setContent(String.valueOf(body.getOrDefault("content", "")));
        n.setIsRead(false);
        notificationMapper.insert(n);
        return Results.ok(n);
    }


    /** 按 Key 散列取接入上下文（billing ingest 校验兜底；缓存主路径见 05 §7.1） */
    @GetMapping("/api-keys/{keyHash}")
    public Result<Map<String, Object>> apiKeyByHash(@PathVariable String keyHash) {
        ApiKey k = apiKeyService.byHash(keyHash);
        if (k == null) return Results.fail("PLT-0404", "API Key 不存在");
        // products 列为 JSON 数组（如 ["Embedding API"]），直接透传原始 JSON 供消费方解析
        List<String> products = k.getProducts() == null ? List.of()
                : List.of(k.getProducts().replace("[", "").replace("]", "").replace("\"", "").split("\\s*,\\s*"));
        var dept = k.getDeptId() == null ? null : departmentMapper.selectById(k.getDeptId());
        String deptName = dept == null ? null : dept.getName();
        return Results.ok(Map.of(
                "keyId", k.getId(),
                "tenantId", k.getTenantId(),
                "deptId", k.getDeptId() == null ? 0 : k.getDeptId(),
                "deptAssigned", k.getDeptId() != null,
                "deptName", deptName == null ? "" : deptName,
                "name", k.getName() == null ? "" : k.getName(),
                "status", k.getStatus(),
                "products", products));
    }
}
