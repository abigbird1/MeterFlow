package com.meterflow.platform.controller;

import com.meterflow.common.api.Results;
import com.meterflow.common.api.Results.Result;
import com.meterflow.platform.dto.PlatformParams.PasswordParam;
import com.meterflow.platform.dto.PlatformParams.ProfileParam;
import com.meterflow.platform.service.ProfileService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 个人信息（05 §5.8）：资料与密码（修改密码属敏感操作，留痕） */
@RestController
@RequestMapping("/t/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    public record PasswordRequest(@NotBlank String oldPassword, @NotBlank String newPassword) {}

    @GetMapping
    public Result<Object> profile() {
        return Results.ok(profileService.profile());
    }

    @PutMapping
    public Result<Void> update(@Valid @RequestBody ProfileParam param) {
        profileService.updateName(param.name());
        return Results.ok();
    }

    @PostMapping("/password")
    public Result<Void> password(@Valid @RequestBody PasswordRequest req) {
        profileService.changePassword(req.oldPassword(), req.newPassword());
        return Results.ok();
    }
}
