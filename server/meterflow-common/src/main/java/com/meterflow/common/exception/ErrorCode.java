package com.meterflow.common.exception;

/** 错误码契约（meterflow.md #7.3）：分段 GWY-/PLT-/BIL-/EQY-/FIN- + 4 位数字 */
public interface ErrorCode {
    String getCode();
    String getMessage();
}
