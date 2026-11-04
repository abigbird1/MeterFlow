package com.meterflow.common.exception;

/** 全局/网关层通用错误码 */
public enum GlobalErrorCode implements ErrorCode {
    SUCCESS("00000", "成功"),
    UNAUTHORIZED("GWY-0401", "令牌无效或已过期"),
    FORBIDDEN("GWY-0403", "权限不足"),
    RATE_LIMITED("GWY-0429", "请求过于频繁，请稍后重试"),
    INTERNAL_ERROR("GWY-5000", "系统繁忙，请稍后重试");

    private final String code;
    private final String message;

    GlobalErrorCode(String code, String message) { this.code = code; this.message = message; }

    @Override public String getCode() { return code; }
    @Override public String getMessage() { return message; }
}
