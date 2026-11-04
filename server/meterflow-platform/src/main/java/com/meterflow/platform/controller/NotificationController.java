package com.meterflow.platform.controller;

import com.meterflow.common.api.Results;
import com.meterflow.common.api.Results.Result;
import com.meterflow.platform.entity.Notification;
import com.meterflow.platform.service.AuthService;
import com.meterflow.platform.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 站内通知（05 §5.8）：列表 / 全部已读 */
@RestController
@RequestMapping("/t/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final AuthService authService;
    private final NotificationService notificationService;

    @GetMapping
    public Result<List<Notification>> list() {
        return Results.ok(notificationService.list(authService.currentTenantId()));
    }

    @PostMapping("/read-all")
    public Result<Void> readAll() {
        notificationService.readAll(authService.currentTenantId());
        return Results.ok();
    }
}
