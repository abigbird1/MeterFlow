package com.meterflow.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meterflow.common.api.Results;
import com.meterflow.common.api.Results.Result;
import com.meterflow.platform.entity.AuditLog;
import com.meterflow.platform.entity.Notification;
import com.meterflow.platform.entity.PlatformUser;
import com.meterflow.platform.entity.Tenant;
import com.meterflow.platform.mapper.AuditLogMapper;
import com.meterflow.platform.mapper.NotificationMapper;
import com.meterflow.platform.mapper.PlatformUserMapper;
import com.meterflow.platform.mapper.TenantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 管理端：租户管理 / 数据看板 / 平台账号 / 审计日志（05 §6.1/6.6） */
@RestController
@RequestMapping("/a")
@RequiredArgsConstructor
public class AdminController {

    private final TenantMapper tenantMapper;
    private final PlatformUserMapper platformUserMapper;
    private final AuditLogMapper auditLogMapper;
    private final NotificationMapper notificationMapper;
    private final com.meterflow.platform.feign.BillingClient billingClient;

    /** 租户列表（余额经 billing Feign 聚合） */
    @GetMapping("/tenants")
    public Result<List<Map<String, Object>>> tenants() {
        List<Tenant> tenants = tenantMapper.selectList(null);
        return Results.ok(tenants.stream().map(t -> {
            Map<String, Object> m = new java.util.HashMap<>();
            m.put("id", t.getId()); m.put("name", t.getName());
            m.put("email", t.getEmail()); m.put("status", t.getStatus());
            m.put("isWhitelist", t.getIsWhitelist());
            var resp = billingClient.account(t.getId());
            Map<String, Object> acc = cast(resp.get("data"));
            m.put("balanceCents", acc == null ? 0 : acc.get("balanceCents"));
            m.put("stage", acc == null ? "normal" : acc.get("status"));
            return m;
        }).collect(Collectors.toList()));
    }

    /** 数据看板（简化聚合：租户数/欠费数/审计数 + 近 7 日平台消费趋势） */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        long tenants = tenantMapper.selectCount(null);
        List<Map<String, Object>> all = tenants().data();
        long debt = all.stream().filter(t -> {
            Object b = t.get("balanceCents");
            return b instanceof Number n && n.longValue() < 0;
        }).count();
        long audits = auditLogMapper.selectCount(null);
        Map<String, Object> out = new java.util.HashMap<>(
                Map.of("tenantCount", tenants, "debtTenants", debt, "auditCount", audits));
        // 近 7 日平台消费趋势；billing 不可用时降级为空，不阻塞看板
        try {
            Object trend = billingClient.platformTrend(7).get("data");
            out.put("trend", trend == null ? List.of() : trend);
        } catch (Exception e) {
            out.put("trend", List.of());
        }
        return Results.ok(out);
    }

    /** 停用/恢复租户（敏感操作，全站拒收语义由计量入口按状态生效） */
    @PostMapping("/tenants/{tenantId}/{action}")
    @com.meterflow.common.audit.AuditLog(action = "停用/恢复租户", sensitive = true)
    public Result<Tenant> toggle(@PathVariable Long tenantId, @PathVariable String action) {
        Tenant t = tenantMapper.selectById(tenantId);
        if (t == null) return Results.fail("PLT-0404", "租户不存在");
        t.setStatus("disable".equals(action) ? "disabled" : "normal");
        tenantMapper.updateById(t);
        Notification n = new Notification();
        n.setTenantId(tenantId); n.setType("warn");
        n.setTitle("disable".equals(action) ? "租户已停用" : "租户已恢复");
        n.setContent("平台管理员操作（留痕）");
        n.setIsRead(false);
        notificationMapper.insert(n);
        return Results.ok(t);
    }

    /** 白名单调整（敏感操作，留痕） */
    @PutMapping("/tenants/{tenantId}/whitelist")
    @com.meterflow.common.audit.AuditLog(action = "白名单调整", sensitive = true)
    public Result<Tenant> whitelist(@PathVariable Long tenantId, @RequestBody WhitelistRequest req) {
        Tenant t = tenantMapper.selectById(tenantId);
        t.setIsWhitelist(req.isWhitelist());
        t.setOverdraftCents(req.overdraftCents());
        tenantMapper.updateById(t);
        return Results.ok(t);
    }

    public record WhitelistRequest(Boolean isWhitelist, Long overdraftCents) {}

    /** 审计日志（倒序前 200 条，保留 1 年） */

    public record NewUserRequest(@jakarta.validation.constraints.NotBlank String name,
                                 @jakarta.validation.constraints.NotBlank String email,
                                 @jakarta.validation.constraints.NotBlank String password,
                                 @jakarta.validation.constraints.NotBlank String role) {}

    /** 新增平台账号（05 §6.6.4，密码 SHA-256 入库） */
    @PostMapping("/platform-users/new")
    @com.meterflow.common.audit.AuditLog(action = "新增平台账号", sensitive = true)
    public Result<PlatformUser> newUser(@RequestHeader("Authorization") String authorization,
                                        @RequestBody NewUserRequest req) {
        PlatformUser u = new PlatformUser();
        u.setName(req.name()); u.setEmail(req.email());
        u.setPasswordHash(cn.dev33.satoken.secure.SaSecureUtil.sha256(req.password()));
        u.setRole(req.role()); u.setStatus("active");
        platformUserMapper.insert(u);
        return Results.ok(u);
    }

    @GetMapping("/audit-logs")
    public Result<List<AuditLog>> auditLogs(@RequestParam(defaultValue = "200") int limit) {
        return Results.ok(auditLogMapper.selectList(new LambdaQueryWrapper<AuditLog>()
                .orderByDesc(AuditLog::getId)
                .last("limit " + Math.min(limit, 200))));
    }

    /** 平台账号（六岗位） */
    @GetMapping("/platform-users")
    public Result<List<PlatformUser>> users() {
        return Results.ok(platformUserMapper.selectList(null));
    }

    @PostMapping("/platform-users/{id}/{action}")
    @com.meterflow.common.audit.AuditLog(action = "平台账号变更", sensitive = true)
    public Result<PlatformUser> userAction(@PathVariable Long id, @PathVariable String action,
                                           @RequestBody(required = false) Map<String, String> body) {
        PlatformUser u = platformUserMapper.selectById(id);
        if (u == null) return Results.fail("PLT-0404", "账号不存在");
        if ("role".equals(action) && body != null) u.setRole(body.get("role"));
        if ("disable".equals(action)) u.setStatus("disabled");
        if ("enable".equals(action)) u.setStatus("active");
        platformUserMapper.updateById(u);
        return Results.ok(u);
    }

    /** 通知（平台触达租户） */
    @PostMapping("/notify/{tenantId}")
    public Result<Notification> notify(@PathVariable Long tenantId, @RequestBody Map<String, String> body) {
        Notification n = new Notification();
        n.setTenantId(tenantId); n.setType(body.getOrDefault("type", "info"));
        n.setTitle(body.getOrDefault("title", "平台通知"));
        n.setContent(body.getOrDefault("content", ""));
        n.setIsRead(false);
        notificationMapper.insert(n);
        return Results.ok(n);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> cast(Object o) { return (Map<String, Object>) o; }
}
