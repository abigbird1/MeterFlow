package com.meterflow.platform.web;

import cn.dev33.satoken.exception.NotLoginException;
import com.meterflow.common.api.Results;
import com.meterflow.common.api.Results.Result;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** platform 侧认证异常：未登录返回 HTTP 401（前端拦截器据此跳转登录页） */
@RestControllerAdvice
public class PlatformExceptionHandler {

    @ExceptionHandler(NotLoginException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> notLogin(NotLoginException e) {
        return Results.fail("GWY-0401", "登录已失效，请重新登录");
    }
}
