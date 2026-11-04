package com.meterflow.equity.exception;

import com.meterflow.common.exception.ErrorCode;

/** equity 分段错误码（meterflow.md #7.3） */
public enum EquityErrorCode implements ErrorCode {
    SOLD_OUT("EQY-4001", "库存不足"),
    PER_LIMIT("EQY-4002", "超出每人限购"),
    ORDER_CLOSED("EQY-4003", "订单已关闭或超时"),
    WINDOW("EQY-4004", "超出开售窗口");

    private final String code;
    private final String message;

    EquityErrorCode(String code, String message) { this.code = code; this.message = message; }

    @Override public String getCode() { return code; }
    @Override public String getMessage() { return message; }
}
