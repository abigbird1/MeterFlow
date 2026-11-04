package com.meterflow.billing.web;

import com.meterflow.common.api.Results;
import com.meterflow.common.api.Results.Result;
import com.meterflow.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * billing 异常 → HTTP 语义映射（05 §2.2）：
 * BIL-0401 → 401；BIL-4031（METER_SUSPENDED，R8）→ 403；其余业务异常 200 + 错误码。
 */
@RestControllerAdvice
public class BillingExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> business(BusinessException e) {
        String code = e.getCode();
        HttpStatus status = switch (code) {
            case "BIL-0401", "GWY-0401" -> HttpStatus.UNAUTHORIZED;
            case "GWY-0403" -> HttpStatus.FORBIDDEN;
            case "BIL-4031" -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.OK;
        };
        return ResponseEntity.status(status).body(Results.fail(code, e.getMessage()));
    }
}
