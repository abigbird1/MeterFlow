package com.meterflow.billing.controller;

import com.meterflow.billing.dto.IngestDtos.EventParam;
import com.meterflow.billing.dto.IngestDtos.IngestResultVO;
import com.meterflow.billing.exception.BillingErrorCode;
import com.meterflow.billing.service.KeyCacheService;
import com.meterflow.billing.service.KeyCacheService.KeyContext;
import com.meterflow.billing.service.MeteringService;
import com.meterflow.common.api.Results;
import com.meterflow.common.api.Results.Result;
import com.meterflow.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 事件上报（05 §4，热路径 P1）：X-API-Key 鉴权 → 双保险判重（R3/R4）→ 计价 → 抵扣 → 入账。
 * 停服拒收：403 + BIL-4031（METER_SUSPENDED，R8）。
 */
@RestController
@RequestMapping("/ingest")
@RequiredArgsConstructor
public class IngestController {

    private final KeyCacheService keyCacheService;
    private final MeteringService meteringService;

    public record BatchRequest(List<EventParam> events) {}

    @PostMapping("/events")
    public Result<IngestResultVO> events(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                         @RequestBody EventParam param) {
        KeyContext ctx = requireKey(apiKey);
        return Results.ok(meteringService.accept(ctx, param));
    }

    /** 批量上报（AF1：≤500 条，逐条幂等返回结果集） */
    @PostMapping("/events/batch")
    public Result<List<IngestResultVO>> batch(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                              @RequestBody BatchRequest req) {
        KeyContext ctx = requireKey(apiKey);
        if (req.events() == null || req.events().isEmpty() || req.events().size() > 500) {
            throw new BusinessException(BillingErrorCode.PRODUCT_NOT_FOUND, "批量条数须在 1~500 之间");
        }
        return Results.ok(meteringService.acceptBatch(ctx, req.events()));
    }

    private KeyContext requireKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) throw new BusinessException(BillingErrorCode.KEY_INVALID);
        KeyContext ctx = keyCacheService.resolve(apiKey);
        if (ctx == null || "disabled".equals(ctx.status())) {
            throw new BusinessException(BillingErrorCode.KEY_INVALID);
        }
        return ctx;
    }
}
