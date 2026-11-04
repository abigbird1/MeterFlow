package com.meterflow.platform.service;

import cn.dev33.satoken.stp.StpUtil;
import com.meterflow.platform.entity.AuditLog;
import com.meterflow.platform.mapper.AuditLogMapper;
import com.meterflow.platform.mapper.MemberMapper;
import com.meterflow.platform.mapper.PlatformUserMapper;
import com.meterflow.platform.entity.Member;
import com.meterflow.platform.entity.PlatformUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 审计留痕（架构原则 P5）：敏感操作强制写入 audit_log（保留 1 年，评审 P-1） */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogMapper auditLogMapper;
    private final MemberMapper memberMapper;
    private final PlatformUserMapper platformUserMapper;

    /** 当前登录人身份描述（操作人+岗位），审计四要素之一（R37） */
    public String currentActor() {
        String loginId = StpUtil.getLoginIdAsString();
        if (loginId.startsWith("p:")) {
            PlatformUser u = platformUserMapper.selectById(Long.valueOf(loginId.substring(2)));
            return u == null ? loginId : u.getName();
        }
        Member m = memberMapper.selectById(Long.valueOf(loginId.substring(2)));
        return m == null ? loginId : m.getName();
    }

    public void record(String action, String target, String scope, boolean sensitive) {
        String loginId = StpUtil.getLoginIdAsString();
        AuditLog a = new AuditLog();
        a.setActorId(loginId.contains(":") ? Long.valueOf(loginId.substring(2)) : null);
        if (loginId.startsWith("p:")) {
            PlatformUser u = platformUserMapper.selectById(Long.valueOf(loginId.substring(2)));
            a.setActorName(u == null ? loginId : u.getName());
            a.setActorRole(u == null ? "—" : u.getRole());
        } else {
            Member m = memberMapper.selectById(Long.valueOf(loginId.substring(2)));
            a.setActorName(m == null ? loginId : m.getName());
            a.setActorRole(m == null ? "—" : "member");
        }
        a.setAction(action);
        a.setTarget(target);
        a.setScope(scope == null ? "—" : scope);
        a.setResult("成功");
        a.setIsSensitive(sensitive);
        a.setCreatedAt(java.time.LocalDateTime.now());
        auditLogMapper.insert(a);
    }
}
