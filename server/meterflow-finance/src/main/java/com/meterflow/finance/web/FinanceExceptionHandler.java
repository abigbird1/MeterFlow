package com.meterflow.finance.web;

import com.meterflow.common.api.Results;
import com.meterflow.common.api.Results.Result;
import com.meterflow.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** finance 异常映射：GWY-0401 → 401；其余业务异常 200 + 错误码 */
@RestControllerAdvice
public class FinanceExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> business(BusinessException e) {
        HttpStatus status = "GWY-0401".equals(e.getCode()) ? HttpStatus.UNAUTHORIZED : HttpStatus.OK;
        return ResponseEntity.status(status).body(Results.fail(e.getCode(), e.getMessage()));
    }
}
