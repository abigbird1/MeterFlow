package com.meterflow.equity.web;

import com.meterflow.common.api.Results;
import com.meterflow.common.api.Results.Result;
import com.meterflow.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * equity 异常 → HTTP 语义映射（meterflow.md #7.3）：
 * 鉴权类 401/403；其余业务异常（售罄/限购/超窗/订单关闭）200 + 错误码（R30-R32 负路径可感知）。
 */
@RestControllerAdvice
public class EquityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> business(BusinessException e) {
        String code = e.getCode();
        HttpStatus status = switch (code) {
            case "EQY-0401", "GWY-0401" -> HttpStatus.UNAUTHORIZED;
            case "GWY-0403" -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.OK;
        };
        return ResponseEntity.status(status).body(Results.fail(code, e.getMessage()));
    }
}
