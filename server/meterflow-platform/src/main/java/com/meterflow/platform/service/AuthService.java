package com.meterflow.platform.service;

import cn.dev33.satoken.secure.SaSecureUtil;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meterflow.platform.dto.PlatformVo;
import com.meterflow.platform.entity.Member;
import com.meterflow.platform.entity.Tenant;
import com.meterflow.platform.entity.PlatformUser;
import com.meterflow.platform.entity.Role;
import com.meterflow.platform.entity.RolePermission;
import com.meterflow.common.exception.BusinessException;
import com.meterflow.common.exception.GlobalErrorCode;
import com.meterflow.platform.mapper.MemberMapper;
import com.meterflow.platform.mapper.PlatformUserMapper;
import com.meterflow.platform.mapper.RoleMapper;
import com.meterflow.platform.mapper.RolePermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 登录认证（两端统一账号体系，portal 区分校验源；登录 ID 约定 m:{memberId} / p:{platformUserId}） */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberMapper memberMapper;
    private final PlatformUserMapper platformUserMapper;
    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final com.meterflow.platform.mapper.TenantMapper tenantMapper;

    public PlatformVo.LoginVO login(String email, String password, String portal) {
        checkLock(email);
        if ("a".equals(portal)) {
            PlatformUser u = platformUserMapper.selectOne(new LambdaQueryWrapper<PlatformUser>()
                    .eq(PlatformUser::getEmail, email).last("limit 1"));
            if (u == null || !u.getPasswordHash().equalsIgnoreCase(SaSecureUtil.sha256(password))) {
                recordFail(email);
                throw new BusinessException(GlobalErrorCode.UNAUTHORIZED, "邮箱或密码错误");
            }
            StpUtil.login("p:" + u.getId());
            clearFail(email);
            return new PlatformVo.LoginVO(StpUtil.getTokenValue(),
                    new PlatformVo.UserVO(u.getId(), u.getName(), u.getEmail(), u.getRole(), "a", null, null),
                    List.of(u.getRole()));
        }
        Member m = memberMapper.selectOne(new LambdaQueryWrapper<Member>()
                .eq(Member::getEmail, email).last("limit 1"));
        if (m == null || !m.getPasswordHash().equalsIgnoreCase(SaSecureUtil.sha256(password))) {
            recordFail(email);
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED, "邮箱或密码错误");
        }
        if (!"active".equals(m.getStatus())) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "成员已被禁用");
        }
        StpUtil.login("m:" + m.getId());
        clearFail(email);
        Role role = roleMapper.selectById(m.getRoleId());
        List<String> perms = rolePermissionMapper.selectList(new LambdaQueryWrapper<RolePermission>()
                        .eq(RolePermission::getRoleId, m.getRoleId()))
                .stream().map(RolePermission::getPermCode).toList();
        return new PlatformVo.LoginVO(StpUtil.getTokenValue(),
                new PlatformVo.UserVO(m.getId(), m.getName(), m.getEmail(),
                        role == null ? "member" : role.getName(), "t", m.getTenantId(), m.getDeptId()),
                perms);
    }


    /** 登录失败锁定（E2）：同邮箱连续 5 次失败锁 10 分钟（内存实现，生产换 Redis） */
    private final java.util.Map<String, int[]> failCounter = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, Long> lockUntil = new java.util.concurrent.ConcurrentHashMap<>();

    private void checkLock(String email) {
        Long until = lockUntil.get(email);
        if (until != null && System.currentTimeMillis() < until) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED, "失败次数过多，账号已临时锁定");
        }
    }

    private void recordFail(String email) {
        int[] c = failCounter.computeIfAbsent(email, k -> new int[1]);
        if (++c[0] >= 5) { lockUntil.put(email, System.currentTimeMillis() + 600_000L); c[0] = 0; }
    }

    /** 登录成功即清零失败计数（E2 语义为“连续失败”：历史偶发失败不累积触发锁定） */
    private void clearFail(String email) {
        failCounter.remove(email);
    }

    /** 密码强度（E2）：≥8 位且同时含字母与数字 */
    private void checkPasswordStrength(String pwd) {
        if (pwd == null || pwd.length() < 8 || !pwd.matches(".*[0-9].*")) {
            throw new BusinessException("PLT-4002", "密码需≥8位且同时包含字母与数字");
        }
    }

    /**
     * 租户自助注册（D1，需求 4.1 注册即开通）：同事务创建租户 + 管理员成员；
     * 预设角色模板（3001 管理员）直接引用；内置免费额度由计价时惰性建行（free_quota 惰性初始化）。
     */
    @Transactional
    public PlatformVo.LoginVO register(String name, String email, String password) {
        checkPasswordStrength(password);
        if (memberMapper.selectCount(new LambdaQueryWrapper<Member>().eq(Member::getEmail, email)) > 0
                || tenantMapper.selectCount(new LambdaQueryWrapper<Tenant>().eq(Tenant::getEmail, email)) > 0) {
            throw new BusinessException("PLT-4001", "邮箱已被注册");
        }
        Tenant t = new Tenant();
        t.setName(name == null || name.isBlank() ? email.split("@")[0] + " 的租户" : name);
        t.setEmail(email); t.setPasswordHash(SaSecureUtil.sha256(password));
        t.setStatus("normal"); t.setIsWhitelist(false);
        tenantMapper.insert(t);

        Member m = new Member();
        m.setTenantId(t.getId()); m.setName(name == null || name.isBlank() ? "管理员" : name);
        m.setEmail(email); m.setPasswordHash(SaSecureUtil.sha256(password));
        m.setDeptId(null); m.setRoleId(3001L);   // 管理员预设角色
        m.setStatus("active");
        memberMapper.insert(m);

        StpUtil.login("m:" + m.getId());
        List<String> perms = rolePermissionMapper.selectList(new LambdaQueryWrapper<RolePermission>()
                        .eq(RolePermission::getRoleId, 3001L))
                .stream().map(RolePermission::getPermCode).toList();
        return new PlatformVo.LoginVO(StpUtil.getTokenValue(),
                new PlatformVo.UserVO(m.getId(), m.getName(), m.getEmail(), "管理员", "t", t.getId(), null),
                perms);
    }

    /** 当前会话用户信息（/auth/me；供网关后各服务经 Feign 解析身份） */
    public PlatformVo.UserVO currentUser() {
        String loginId = StpUtil.getLoginIdAsString();
        if (loginId.startsWith("p:")) {
            PlatformUser u = platformUserMapper.selectById(Long.valueOf(loginId.substring(2)));
            // 会话仍有效但账号已删除：视为登录失效（避免 NPE 500）
            if (u == null) throw new BusinessException("GWY-0401", "登录已失效，请重新登录");
            return new PlatformVo.UserVO(u.getId(), u.getName(), u.getEmail(), u.getRole(), "a", null, null);
        }
        Member m = memberMapper.selectById(Long.valueOf(loginId.substring(2)));
        if (m == null) throw new BusinessException("GWY-0401", "登录已失效，请重新登录");
        Role role = roleMapper.selectById(m.getRoleId());
        return new PlatformVo.UserVO(m.getId(), m.getName(), m.getEmail(),
                role == null ? "member" : role.getName(), "t", m.getTenantId(), m.getDeptId());
    }

    /** 当前登录成员的租户 ID（租户端接口的租户上下文） */
    public Long currentTenantId() {
        String loginId = StpUtil.getLoginIdAsString();
        if (loginId.startsWith("m:")) {
            Member m = memberMapper.selectById(Long.valueOf(loginId.substring(2)));
            return m.getTenantId();
        }
        throw new BusinessException(GlobalErrorCode.FORBIDDEN, "平台账号无租户上下文");
    }
}
