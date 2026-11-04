package com.meterflow.common.api;

/** 统一响应封装（meterflow.md #7.3）：{code, message, data}，code=00000 成功 */
public final class Results {

    public static final String SUCCESS = "00000";

    private Results() {}

    public static <T> Result<T> ok(T data) {
        return new Result<>(SUCCESS, "成功", data);
    }

    public static Result<Void> ok() {
        return new Result<>(SUCCESS, "成功", null);
    }

    public static <T> Result<T> fail(String code, String message) {
        return new Result<>(code, message, null);
    }

    public record Result<T>(String code, String message, T data) {}
}
