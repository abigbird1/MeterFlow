package com.meterflow.platform.dto;

import java.util.List;

/** 请求参数集中定义（meterflow.md #5.7：入参校验注解在 Controller 层配合 jakarta-validation 使用） */
public final class PlatformParams {

    private PlatformParams() {}

    public record LoginParam(String email, String password, String portal) {}
    public record ApiKeyCreateParam(String name, String note, Long deptId, List<String> products) {}
    public record ApiKeyStatusParam(String action) {}
    public record DeptCreateParam(Long parentId, String name) {}
    public record MemberInviteParam(String email, String name, Long deptId, Long roleId) {}
    public record MemberRoleParam(Long roleId) {}
    public record MemberStatusParam(String status) {}
    public record RoleCreateParam(String name, List<String> perms) {}
    public record ProfileParam(String name) {}
    public record PasswordParam(String oldPassword, String newPassword) {}
}
