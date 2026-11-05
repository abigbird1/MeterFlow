package com.meterflow.finance.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meterflow.common.api.Results;
import com.meterflow.common.api.Results.Result;
import com.meterflow.finance.entity.Bill;
import com.meterflow.finance.entity.Dispute;
import com.meterflow.finance.entity.DisputeReply;
import com.meterflow.finance.service.BillService;
import com.meterflow.finance.service.DisputeService;
import com.meterflow.finance.service.IdentityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 租户端争议申诉（05 §5.5；发起校验 R44：已出账/已确认可发起，已结清不可） */
@RestController
@RequestMapping("/t/disputes")
@RequiredArgsConstructor
public class TenantDisputeController {

    private final DisputeService disputeService;
    private final BillService billService;
    private final IdentityService identityService;

    public record CreateRequest(@NotBlank String billNo, @NotNull Long amountCents, @NotBlank String reason) {}

    private record Me(Long tenantId, String name) {}

    private Me me(String authorization) {
        var me = identityService.require(authorization);
        if (me == null) throw new com.meterflow.common.exception.BusinessException("GWY-0401", "登录已失效，请重新登录");
        if (me.tenantId() == null) throw new com.meterflow.common.exception.BusinessException("GWY-0401", "平台账号无租户上下文");
        return new Me(me.tenantId(), me.name());
    }

    @GetMapping
    public Result<List<Dispute>> list(@RequestHeader("Authorization") String authorization) {
        Long tenantId = me(authorization).tenantId();
        return Results.ok(disputeService.list().stream()
                .filter(d -> d.getTenantId().equals(tenantId)).toList());
    }

    @GetMapping("/{disputeNo}")
    public Result<Map<String, Object>> detail(@RequestHeader("Authorization") String authorization,
                                              @PathVariable String disputeNo) {
        Long tenantId = me(authorization).tenantId();
        Dispute d = disputeService.detail(disputeNo);
        if (!d.getTenantId().equals(tenantId)) throw new com.meterflow.common.exception.BusinessException("GWY-0403", "无权查看");
        List<DisputeReply> replies = disputeService.replies(d.getId());
        return Results.ok(java.util.Map.of("dispute", d, "replies", replies));
    }

    @PostMapping
    public Result<Dispute> create(@RequestHeader("Authorization") String authorization,
                                  @Valid @RequestBody CreateRequest req) {
        Long tenantId = me(authorization).tenantId();
        Bill bill = billService.detail(req.billNo());
        if (bill == null || !bill.getTenantId().equals(tenantId)) {
            throw new com.meterflow.common.exception.BusinessException("FIN-4002", "账单不存在");
        }
        // R44：已出账/已确认可发起；已结清不可
        if (!"draft".equals(bill.getStatus()) && !"confirmed".equals(bill.getStatus())) {
            throw new com.meterflow.common.exception.BusinessException("FIN-4002",
                    "当前账单状态不可发起争议（R44）");
        }
        Dispute d = new Dispute();
        d.setDisputeNo("DSP" + System.currentTimeMillis());
        d.setTenantId(tenantId); d.setBillNo(req.billNo());
        d.setAmountCents(BigDecimal.valueOf(req.amountCents()));
        d.setReason(req.reason());
        d.setStatus("pending");
        d.setSlaHours(req.amountCents() >= 100000 ? 24 : 48);   // 分：≥1000 元 24h
        d.setSlaDeadline(LocalDateTime.now().plusHours(d.getSlaHours()));
        d.setIsEscalated(false); d.setCreatedAt(LocalDateTime.now());
        disputeService.create(d);
        return Results.ok(d);
    }
}
