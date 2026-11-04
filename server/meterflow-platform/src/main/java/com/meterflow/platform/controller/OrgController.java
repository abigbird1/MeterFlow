package com.meterflow.platform.controller;

import com.meterflow.common.api.PageResult;
import com.meterflow.common.api.Results;
import com.meterflow.common.api.Results.Result;
import com.meterflow.platform.dto.PlatformParams.DeptCreateParam;
import com.meterflow.platform.dto.PlatformParams.MemberInviteParam;
import com.meterflow.platform.dto.PlatformParams.MemberRoleParam;
import com.meterflow.platform.dto.PlatformParams.MemberStatusParam;
import com.meterflow.platform.dto.PlatformParams.RoleCreateParam;
import com.meterflow.platform.dto.PlatformVo.DeptNode;
import com.meterflow.platform.entity.Member;
import com.meterflow.platform.entity.Role;
import com.meterflow.platform.entity.RolePermission;
import com.meterflow.platform.service.AuthService;
import com.meterflow.platform.service.OrgService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 组织与角色（05 §5.7）：部门树 / 成员 / 角色（权限点勾选） */
@RestController
@RequestMapping("/t/org")
@RequiredArgsConstructor
public class OrgController {

    private final AuthService authService;
    private final OrgService orgService;

    public record DeptRequest(@NotBlank String name) {}
    public record InviteRequest(@NotBlank String email, @NotBlank String name, Long deptId, Long roleId) {}
    public record RoleRequest(@NotBlank String name, List<String> perms) {}

    @GetMapping("/departments")
    public Result<List<DeptNode>> departments() {
        return Results.ok(orgService.deptTree(authService.currentTenantId()));
    }

    @PostMapping("/departments/{parentId}")
    public Result<Void> addDepartment(@PathVariable Long parentId, @Valid @RequestBody DeptRequest req) {
        orgService.createDept(authService.currentTenantId(), parentId, req.name());
        return Results.ok();
    }

    @GetMapping("/members")
    public Result<PageResult<Member>> members(@RequestParam(defaultValue = "1") int pageNum,
                                              @RequestParam(defaultValue = "20") int pageSize,
                                              @RequestParam(required = false) Long deptId) {
        // 骨架阶段返回全量（成员规模小）；分页在成员规模上升后启用
        List<Member> list = orgService.members(authService.currentTenantId(), deptId);
        return Results.ok(new PageResult<>(list, list.size(), pageNum, pageSize));
    }

    @PostMapping("/members")
    public Result<Member> invite(@Valid @RequestBody InviteRequest req) {
        return Results.ok(orgService.invite(authService.currentTenantId(),
                req.email(), req.name(), req.deptId(), req.roleId()));
    }

    @PostMapping("/members/{memberId}/role")
    public Result<Void> changeRole(@PathVariable Long memberId, @RequestBody MemberRoleParam param) {
        orgService.changeRole(authService.currentTenantId(), memberId, param.roleId());
        return Results.ok();
    }

    @PostMapping("/members/{memberId}/status")
    public Result<Void> changeStatus(@PathVariable Long memberId, @RequestBody MemberStatusParam param) {
        orgService.changeStatus(authService.currentTenantId(), memberId, param.status());
        return Results.ok();
    }

    @GetMapping("/roles")
    public Result<List<Role>> roles() {
        return Results.ok(orgService.roles(authService.currentTenantId()));
    }

    @GetMapping("/roles/{roleId}/permissions")
    public Result<List<RolePermission>> permissions(@PathVariable Long roleId) {
        return Results.ok(orgService.permissions(roleId));
    }

    @PostMapping("/roles")
    public Result<Role> createRole(@Valid @RequestBody RoleRequest req) {
        return Results.ok(orgService.createRole(authService.currentTenantId(), req.name(), req.perms()));
    }
}
