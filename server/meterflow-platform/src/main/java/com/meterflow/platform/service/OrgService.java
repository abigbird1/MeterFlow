package com.meterflow.platform.service;

import cn.dev33.satoken.secure.SaSecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meterflow.platform.dto.PlatformVo;
import com.meterflow.platform.entity.Department;
import com.meterflow.platform.entity.Member;
import com.meterflow.platform.entity.Role;
import com.meterflow.platform.entity.RolePermission;
import com.meterflow.common.exception.BusinessException;
import com.meterflow.common.exception.GlobalErrorCode;
import com.meterflow.platform.mapper.DepartmentMapper;
import com.meterflow.platform.mapper.MemberMapper;
import com.meterflow.platform.mapper.RoleMapper;
import com.meterflow.platform.mapper.RolePermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 组织域（M1）：部门树 / 成员 / 角色（预设+自定义，权限点勾选） */
@Service
@RequiredArgsConstructor
public class OrgService {

    private final DepartmentMapper departmentMapper;
    private final MemberMapper memberMapper;
    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final AuthService authService;
    private final AuditService auditService;

    /* ---------- 部门 ---------- */

    public List<PlatformVo.DeptNode> deptTree(Long tenantId) {
        List<Department> all = departmentMapper.selectList(new LambdaQueryWrapper<Department>()
                .eq(Department::getTenantId, tenantId));
        Map<Long, List<Department>> byParent = all.stream()
                .collect(Collectors.groupingBy(d -> d.getParentId() == null ? 0L : d.getParentId()));
        return buildNodes(byParent, 0L);
    }

    private List<PlatformVo.DeptNode> buildNodes(Map<Long, List<Department>> byParent, Long parentId) {
        List<PlatformVo.DeptNode> nodes = new ArrayList<>();
        for (Department d : byParent.getOrDefault(parentId, List.of())) {
            nodes.add(new PlatformVo.DeptNode(d.getId(), d.getName(), d.getParentId(), buildNodes(byParent, d.getId())));
        }
        return nodes;
    }

    public void createDept(Long tenantId, Long parentId, String name) {
        Department d = new Department();
        d.setTenantId(tenantId);
        d.setParentId(parentId);
        d.setName(name);
        departmentMapper.insert(d);
    }

    /* ---------- 成员 ---------- */

    public List<Member> members(Long tenantId, Long deptId) {
        LambdaQueryWrapper<Member> qw = new LambdaQueryWrapper<Member>().eq(Member::getTenantId, tenantId);
        if (deptId != null) qw.eq(Member::getDeptId, deptId);
        return memberMapper.selectList(qw.orderByDesc(Member::getId));
    }

    /** 邀请成员（直接生效 B11）：初始口令 demo（SHA-256 入库），首次登录后建议修改 */
    public Member invite(Long tenantId, String email, String name, Long deptId, Long roleId) {
        Long exists = memberMapper.selectCount(new LambdaQueryWrapper<Member>()
                .eq(Member::getTenantId, tenantId).eq(Member::getEmail, email));
        if (exists > 0) throw new BusinessException(GlobalErrorCode.FORBIDDEN, "邮箱已被占用");
        Member m = new Member();
        m.setTenantId(tenantId);
        m.setName(name);
        m.setEmail(email);
        m.setPasswordHash(SaSecureUtil.sha256("demo"));
        m.setDeptId(deptId);
        m.setRoleId(roleId);
        m.setStatus("active");
        memberMapper.insert(m);
        auditService.record("邀请成员", name, "邮箱 " + email, false);
        return m;
    }

    public void changeRole(Long tenantId, Long memberId, Long roleId) {
        Member m = memberMapper.selectById(memberId);
        requireSameTenant(m, tenantId);
        m.setRoleId(roleId);
        memberMapper.updateById(m);
    }

    public void changeStatus(Long tenantId, Long memberId, String status) {
        Member m = memberMapper.selectById(memberId);
        requireSameTenant(m, tenantId);
        m.setStatus(status);
        memberMapper.updateById(m);
        auditService.record(status.equals("disabled") ? "禁用成员" : "启用成员", m.getName(), m.getEmail(), true);
    }

    private void requireSameTenant(Member m, Long tenantId) {
        if (m == null || !m.getTenantId().equals(tenantId)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "成员不存在或无权操作");
        }
    }

    /* ---------- 角色 ---------- */

    public List<Role> roles(Long tenantId) {
        // 预设模板（tenant_id IS NULL）+ 本租户自定义
        return roleMapper.selectList(new LambdaQueryWrapper<Role>()
                .and(qw -> qw.isNull(Role::getTenantId).or().eq(Role::getTenantId, tenantId))
                .orderByAsc(Role::getId));
    }

    public List<RolePermission> permissions(Long roleId) {
        return rolePermissionMapper.selectList(new LambdaQueryWrapper<RolePermission>()
                .eq(RolePermission::getRoleId, roleId));
    }

    @Transactional
    public Role createRole(Long tenantId, String name, List<String> perms) {
        Role r = new Role();
        r.setTenantId(tenantId);
        r.setName(name);
        r.setRoleType("custom");
        r.setStatus("active");
        roleMapper.insert(r);
        for (String perm : perms) {
            RolePermission rp = new RolePermission();
            rp.setRoleId(r.getId());
            rp.setPermCode(perm);
            rolePermissionMapper.insert(rp);
        }
        auditService.record("新建自定义角色", name, "权限点 " + perms.size() + " 个", true);
        return r;
    }
}
