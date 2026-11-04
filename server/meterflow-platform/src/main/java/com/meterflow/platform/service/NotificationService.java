package com.meterflow.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.meterflow.platform.entity.Notification;
import com.meterflow.platform.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** 站内通知（M12）：列表与全部已读 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;

    public List<Notification> list(Long tenantId) {
        return notificationMapper.selectList(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getTenantId, tenantId)
                .orderByDesc(Notification::getId));
    }

    public void readAll(Long tenantId) {
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getTenantId, tenantId)
                .set(Notification::getIsRead, true));
    }
}
