package com.meterflow.platform.dto;

import java.util.List;

/** 出参视图（金额字段本域无；字段与 04 表结构一致） */
public final class PlatformVo {

    private PlatformVo() {}

    public record LoginVO(String token, UserVO user, List<String> permissions) {}
    /** tenantId/deptId：租户成员携带（跨服务身份解析与部门隔离依据）；平台账号为 NULL */
    public record UserVO(Long id, String name, String email, String role, String portal,
                         Long tenantId, Long deptId) {}
    public record DeptNode(Long id, String name, Long parentId, List<DeptNode> children) {}
}
