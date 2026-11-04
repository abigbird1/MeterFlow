package com.meterflow.platform.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.meterflow.common.api.Results;
import com.meterflow.common.api.Results.Result;
import com.meterflow.platform.dto.PlatformParams.LoginParam;
import com.meterflow.platform.dto.PlatformVo.LoginVO;
import com.meterflow.platform.dto.PlatformVo.UserVO;
import com.meterflow.platform.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 认证（05 §3）：登录 / 登出 / 当前身份 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {

    private final AuthService authService;

    public record LoginRequest(@NotBlank String email, @NotBlank String password, @NotBlank String portal) {}


    public record RegisterRequest(@NotBlank String name, @NotBlank String email, @NotBlank String password) {}

    /** 租户自助注册（D1）：注册即开通 + 管理员成员 + 内置额度（计价时惰性生效） */
    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterRequest req) {
        return Results.ok(authService.register(req.name(), req.email(), req.password()));
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginParam param) {
        return Results.ok(authService.login(param.email(), param.password(), param.portal()));
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        StpUtil.logout();
        return Results.ok();
    }

    @GetMapping("/me")
    public Result<UserVO> me() {
        return Results.ok(authService.currentUser());
    }
}
