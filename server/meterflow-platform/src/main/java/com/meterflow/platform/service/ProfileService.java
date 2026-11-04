package com.meterflow.platform.service;

import cn.dev33.satoken.secure.SaSecureUtil;
import cn.dev33.satoken.stp.StpUtil;
import com.meterflow.platform.entity.Member;
import com.meterflow.platform.entity.PlatformUser;
import com.meterflow.common.exception.BusinessException;
import com.meterflow.common.exception.GlobalErrorCode;
import com.meterflow.platform.mapper.MemberMapper;
import com.meterflow.platform.mapper.PlatformUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 个人信息（M1：基础资料与密码） */
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final MemberMapper memberMapper;
    private final PlatformUserMapper platformUserMapper;

    public Object profile() {
        String loginId = StpUtil.getLoginIdAsString();
        return loginId.startsWith("m:")
                ? memberMapper.selectById(Long.valueOf(loginId.substring(2)))
                : platformUserMapper.selectById(Long.valueOf(loginId.substring(2)));
    }

    public void updateName(String name) {
        String loginId = StpUtil.getLoginIdAsString();
        if (loginId.startsWith("m:")) {
            Member m = memberMapper.selectById(Long.valueOf(loginId.substring(2)));
            m.setName(name);
            memberMapper.updateById(m);
        } else {
            PlatformUser u = platformUserMapper.selectById(Long.valueOf(loginId.substring(2)));
            u.setName(name);
            platformUserMapper.updateById(u);
        }
    }

    public void changePassword(String oldPassword, String newPassword) {
        String loginId = StpUtil.getLoginIdAsString();
        if (loginId.startsWith("m:")) {
            Member m = memberMapper.selectById(Long.valueOf(loginId.substring(2)));
            if (!m.getPasswordHash().equalsIgnoreCase(SaSecureUtil.sha256(oldPassword))) {
                throw new BusinessException(GlobalErrorCode.FORBIDDEN, "原密码错误");
            }
            m.setPasswordHash(SaSecureUtil.sha256(newPassword));
            memberMapper.updateById(m);
        } else {
            PlatformUser u = platformUserMapper.selectById(Long.valueOf(loginId.substring(2)));
            if (!u.getPasswordHash().equalsIgnoreCase(SaSecureUtil.sha256(oldPassword))) {
                throw new BusinessException(GlobalErrorCode.FORBIDDEN, "原密码错误");
            }
            u.setPasswordHash(SaSecureUtil.sha256(newPassword));
            platformUserMapper.updateById(u);
        }
    }
}
