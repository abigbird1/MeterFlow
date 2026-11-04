package com.meterflow.billing.exception;

import com.meterflow.common.exception.ErrorCode;

/** billing 分段错误码（meterflow.md #7.3；BIL-4031 对应 R8 停服拒收） */
public enum BillingErrorCode implements ErrorCode {
    SUSPENDED("BIL-4031", "产品停服中，事件被拒收（METER_SUSPENDED）"),
    KEY_INVALID("BIL-0401", "API Key 无效或已禁用"),
    PRODUCT_NOT_FOUND("BIL-0404", "产品不存在或未上架"),
    RECHARGE_LIMIT("BIL-4005", "超出充值单笔限额");

    private final String code;
    private final String message;

    BillingErrorCode(String code, String message) { this.code = code; this.message = message; }

    @Override public String getCode() { return code; }
    @Override public String getMessage() { return message; }
}
