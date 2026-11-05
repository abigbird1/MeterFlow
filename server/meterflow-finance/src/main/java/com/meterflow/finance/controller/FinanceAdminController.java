package com.meterflow.finance.controller;

import com.meterflow.common.api.Results;
import com.meterflow.common.api.Results.Result;
import com.meterflow.finance.entity.AdjustmentOrder;
import com.meterflow.finance.entity.Bill;
import com.meterflow.finance.entity.Dispute;
import com.meterflow.finance.entity.DailyRecon;
import com.meterflow.finance.entity.ReconError;
import com.meterflow.finance.service.BillService;
import com.meterflow.finance.service.DisputeService;
import com.meterflow.common.exception.BusinessException;
import com.meterflow.finance.service.IdentityService;
import com.meterflow.finance.service.IdentityService.Me;
import com.meterflow.finance.service.IdentityService.Me;
import com.meterflow.finance.service.ReconService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.time.LocalDate;
import java.util.Map;

/** 管理端（05 §6.3/6.4/6.5）：账单/任务/对账/调整单/争议 */
@RestController
@RequestMapping("/a")
@RequiredArgsConstructor
public class FinanceAdminController {

    private final BillService billService;
    private final ReconService reconService;
    private final DisputeService disputeService;
    private final IdentityService identityService;

    public record MonthlyBillRequest(Long tenantId, String period) {}
    public record AdjustRequest(String adjType, Long amountCents, String reason) {}
    public record ReviewRequest(Integer stage, Boolean result) {}
    public record ReplyRequest(String content) {}
    public record CompensateRequest(String type, Long amountCents, String note) {}
    public record ProxyRequest(String scope) {}

    private Me me(String authorization) {
        Me me = identityService.require(authorization);
        if (me == null) throw new BusinessException("GWY-0401", "登录已失效，请重新登录");
        return me;
    }

    /** A5：账单操作权限（6.2 矩阵：财务/管理员） */
    private Me requireFinance(String authorization) {
        Me me = me(authorization);
        if (!"fin".equals(me.role()) && !"sadmin".equals(me.role())) {
            throw new BusinessException("GWY-0403", "账单操作岗为财务/管理员");
        }
        return me;
    }

    /* ---------- 账单与任务（6.3） ---------- */

    @GetMapping("/bills")
    public Result<List<Bill>> bills(@RequestHeader("Authorization") String authorization) {
        me(authorization);
        return Results.ok(billService.list());
    }

    @PostMapping("/bills/{billNo}/confirm")
    public Result<Bill> confirm(@RequestHeader("Authorization") String authorization,
                                @PathVariable String billNo) {
        requireFinance(authorization);
        me(authorization);
        return Results.ok(billService.confirm(billNo));
    }

    @PostMapping("/bills/{billNo}/settle")
    public Result<Bill> settle(@RequestHeader("Authorization") String authorization,
                               @PathVariable String billNo) {
        requireFinance(authorization);
        me(authorization);
        return Results.ok(billService.settle(billNo));
    }

    @PostMapping("/tasks/daily-cut")
    public Result<DailyRecon> dailyCut(@RequestHeader("Authorization") String authorization,
                                       @RequestParam(required = false) String date) {
        me(authorization);
        LocalDate d = date == null || date.isBlank()
                ? LocalDate.now().minusDays(1)
                : LocalDate.parse(date);
        return Results.ok(reconService.run(d));
    }

    @PostMapping("/tasks/monthly-bill")
    public Result<Bill> monthlyBill(@RequestHeader("Authorization") String authorization,
                                    @RequestBody MonthlyBillRequest req) {
        requireFinance(authorization);
        me(authorization);
        return Results.ok(billService.monthlyBill(
                req.tenantId() == null ? 1001L : req.tenantId(),
                req.period() == null ? "2026-11" : req.period()));
    }

    /** 月末全员出账手动触发（正常由定时任务在每月最后一天 23:30 执行，R17/R42） */
    @PostMapping("/tasks/monthly-bill-all")
    public Result<Map<String, Object>> monthlyBillAll(@RequestHeader("Authorization") String authorization,
                                                      @RequestBody MonthlyBillRequest req) {
        requireFinance(authorization);
        me(authorization);
        String period = req.period() == null
                ? java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"))
                : req.period();
        return Results.ok(billService.monthlyBillAll(period));
    }

    /** SLA 超时升级手动触发（正常由定时任务每 5 分钟执行，R36；返回本次升级单数） */
    @PostMapping("/tasks/sla-escalate")
    public Result<Integer> slaEscalate(@RequestHeader("Authorization") String authorization) {
        me(authorization);
        return Results.ok(disputeService.escalateOverdue());
    }

    /* ---------- 对账中心（6.4） ---------- */

    @GetMapping("/recon/daily")
    public Result<List<DailyRecon>> reconDaily(@RequestHeader("Authorization") String authorization) {
        me(authorization);
        return Results.ok(reconService.daily());
    }

    @PostMapping("/recon/daily/run")
    public Result<DailyRecon> reconRun(@RequestHeader("Authorization") String authorization,
                                       @RequestParam(required = false) String date) {
        me(authorization);
        LocalDate d = date == null || date.isBlank()
                ? LocalDate.now().minusDays(1)
                : LocalDate.parse(date);
        return Results.ok(reconService.run(d));
    }

    @GetMapping("/recon/errors")
    public Result<List<ReconError>> errors(@RequestHeader("Authorization") String authorization) {
        me(authorization);
        return Results.ok(reconService.errors());
    }

    @GetMapping("/adjustments")
    public Result<List<AdjustmentOrder>> adjustments(@RequestHeader("Authorization") String authorization) {
        me(authorization);
        return Results.ok(reconService.adjustments());
    }

    @PostMapping("/recon/errors/{errNo}/adjustments")
    public Result<AdjustmentOrder> createAdjustment(@RequestHeader("Authorization") String authorization,
                                                    @PathVariable String errNo,
                                                    @RequestBody AdjustRequest req) {
        Me me = me(authorization);
        return Results.ok(reconService.createAdjustment(errNo, req.adjType(),
                req.amountCents() == null ? 0 : req.amountCents(), req.reason(), me.name()));
    }

    @PostMapping("/recon/errors/{errNo}/manual")
    public Result<Void> manual(@RequestHeader("Authorization") String authorization,
                               @PathVariable String errNo) {
        Me me = me(authorization);
        reconService.manual(errNo, me.name());
        return Results.ok();
    }

    /** D5：转人工处置完成（人工核验后关闭差错单） */
    @PostMapping("/recon/errors/{errNo}/close-manual")
    public Result<Void> closeManual(@RequestHeader("Authorization") String authorization,
                                    @PathVariable String errNo) {
        Me me = me(authorization);
        reconService.closeManual(errNo, me.name());
        return Results.ok();
    }

    @PostMapping("/adjustments/{adjNo}/review")
    public Result<AdjustmentOrder> review(@RequestHeader("Authorization") String authorization,
                                          @PathVariable String adjNo,
                                          @RequestBody ReviewRequest req) {
        Me me = me(authorization);
        return Results.ok(reconService.review(adjNo, req.stage(), Boolean.TRUE.equals(req.result()),
                me.name(), me.role()));
    }

    /* ---------- 争议工单（6.5） ---------- */

    @GetMapping("/disputes")
    public Result<List<Dispute>> disputes(@RequestHeader("Authorization") String authorization) {
        me(authorization);
        return Results.ok(disputeService.list());
    }

    @GetMapping("/disputes/{disputeNo}")
    public Result<Dispute> dispute(@RequestHeader("Authorization") String authorization,
                                   @PathVariable String disputeNo) {
        me(authorization);
        return Results.ok(disputeService.detail(disputeNo));
    }

    @PostMapping("/disputes/{disputeNo}/accept")
    public Result<Dispute> accept(@RequestHeader("Authorization") String authorization,
                                  @PathVariable String disputeNo) {
        Me me = me(authorization);
        return Results.ok(disputeService.accept(disputeNo, me.name()));
    }

    @PostMapping("/disputes/{disputeNo}/reply")
    public Result<Void> reply(@RequestHeader("Authorization") String authorization,
                              @PathVariable String disputeNo,
                              @RequestBody ReplyRequest req) {
        Me me = me(authorization);
        disputeService.reply(disputeNo, me.name(), req.content());
        return Results.ok();
    }

    /** 代查（R37）：返回该租户账单概览；调用方需记录审计四要素 */
    @PostMapping("/disputes/{disputeNo}/proxy-query")
    public Result<List<Bill>> proxy(@RequestHeader("Authorization") String authorization,
                                    @PathVariable String disputeNo,
                                    @RequestBody ProxyRequest req) {
        Me me = me(authorization);
        Dispute d = disputeService.detail(disputeNo);
        return Results.ok(billService.byTenant(d.getTenantId()));
    }

    @PostMapping("/disputes/{disputeNo}/compensate")
    public Result<Dispute> compensate(@RequestHeader("Authorization") String authorization,
                                      @PathVariable String disputeNo,
                                      @RequestBody CompensateRequest req) {
        Me me = me(authorization);
        return Results.ok(disputeService.compensate(disputeNo, req.type(),
                req.amountCents() == null ? 0 : req.amountCents(), req.note(), me.name()));
    }

    @PostMapping("/disputes/{disputeNo}/close")
    public Result<Dispute> close(@RequestHeader("Authorization") String authorization,
                                 @PathVariable String disputeNo) {
        Me me = me(authorization);
        return Results.ok(disputeService.close(disputeNo, me.name()));
    }
}
