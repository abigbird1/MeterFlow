package com.meterflow.platform.controller;

import com.meterflow.common.api.Results;
import com.meterflow.common.api.Results.Result;
import com.meterflow.platform.dto.PlatformParams.ApiKeyCreateParam;
import com.meterflow.platform.dto.PlatformParams.ApiKeyStatusParam;
import com.meterflow.platform.entity.ApiKey;
import com.meterflow.platform.service.ApiKeyService;
import com.meterflow.platform.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** API Key 管理（05 §5.6）：hash 存储 ET4，明文仅创建/轮换时一次性返回 */
@RestController
@RequestMapping("/t/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final AuthService authService;
    private final ApiKeyService apiKeyService;

    public record CreateRequest(@NotBlank String name, String note, Long deptId, List<String> products) {}
    public record StatusRequest(@NotBlank String action) {}

    @GetMapping
    public Result<List<ApiKey>> list() {
        return Results.ok(apiKeyService.list(authService.currentTenantId()));
    }

    @PostMapping
    public Result<ApiKey> create(@Valid @RequestBody CreateRequest req) {
        return Results.ok(apiKeyService.create(authService.currentTenantId(),
                req.name(), req.note(), req.deptId(), req.products()));
    }

    @PostMapping("/{id}/status")
    public Result<Void> status(@PathVariable Long id, @Valid @RequestBody StatusRequest req) {
        apiKeyService.changeStatus(authService.currentTenantId(), id, req.action());
        return Results.ok();
    }

    @PostMapping("/{id}/rotate")
    public Result<ApiKey> rotate(@PathVariable Long id) {
        return Results.ok(apiKeyService.rotate(authService.currentTenantId(), id));
    }
}
