package com.meterflow.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meterflow.finance.entity.AdjustmentOrder;
import com.meterflow.finance.entity.Dispute;
import com.meterflow.finance.entity.DisputeReply;
import com.meterflow.finance.mapper.AdjustmentOrderMapper;
import com.meterflow.finance.mapper.DisputeMapper;
import com.meterflow.finance.mapper.DisputeReplyMapper;
import com.meterflow.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.List;
import java.util.UUID;

/** 争议工单（M10）：受理/回复/代查/补偿（审批制 R38）/SLA 升级标记（R36） */
@Service
@RequiredArgsConstructor
public class DisputeService {

    private final DisputeMapper disputeMapper;
    private final DisputeReplyMapper replyMapper;
    private final AdjustmentOrderMapper adjustmentMapper;
    private final com.meterflow.finance.feign.NotifyClient notifyClient;

    public List<Dispute> list() {
        List<Dispute> all = disputeMapper.selectList(new LambdaQueryWrapper<Dispute>()
                .orderByDesc(Dispute::getId));
        LocalDateTime now = LocalDateTime.now();
        for (Dispute d : all) {
            // 惰性兜底：定时任务间隙打开页面也能看到最新升级态（正常由定时任务写入）
            if (!"solved".equals(d.getStatus()) && !"closed".equals(d.getStatus())
                    && !Boolean.TRUE.equals(d.getIsEscalated())
                    && d.getSlaDeadline() != null && now.isAfter(d.getSlaDeadline())) {
                d.setIsEscalated(true);
                disputeMapper.updateById(d);
            }
        }
        return all;
    }

    /** SLA 超时自动升级（R36）：待处理/处理中且超过 slaDeadline → 标记升级并通知租户；返回升级单数 */
    @Transactional
    public int escalateOverdue() {
        List<Dispute> overdue = disputeMapper.selectList(new LambdaQueryWrapper<Dispute>()
                .in(Dispute::getStatus, "pending", "processing")
                .isNotNull(Dispute::getSlaDeadline)
                .lt(Dispute::getSlaDeadline, LocalDateTime.now())
                .and(w -> w.isNull(Dispute::getIsEscalated).or().eq(Dispute::getIsEscalated, false)));
        for (Dispute d : overdue) {
            d.setIsEscalated(true);
            disputeMapper.updateById(d);
            try {
                notifyClient.notify(Map.of("tenantId", d.getTenantId(), "type", "warn",
                        "title", "争议工单已升级",
                        "content", "工单 " + d.getDisputeNo() + " 超出 SLA（" + d.getSlaHours() + " 小时），已自动升级优先处理（R36）"));
            } catch (Exception ignored) { }
        }
        return overdue.size();
    }

    public Dispute detail(String disputeNo) {
        return require(disputeNo);
    }

    public List<DisputeReply> replies(Long disputeId) {
        return replyMapper.selectList(new LambdaQueryWrapper<DisputeReply>()
                .eq(DisputeReply::getDisputeId, disputeId).orderByAsc(DisputeReply::getId));
    }

    @Transactional
    public Dispute create(Dispute d) { disputeMapper.insert(d); return d; }

    @Transactional
    public Dispute accept(String disputeNo, String operator) {
        Dispute d = require(disputeNo);
        if (!"pending".equals(d.getStatus())) throw new BusinessException("FIN-4001", "工单状态不允许受理");
        d.setStatus("processing");
        disputeMapper.updateById(d);
        reply(d, operator + "（客服）", "工单已受理，开始核验相关流水。");
        try {
            notifyClient.notify(Map.of("tenantId", d.getTenantId(), "type", "info",
                    "title", "争议已受理", "content", "工单 " + d.getDisputeNo() + " 已受理，处理中。"));
        } catch (Exception ignored) { }
        return d;
    }

    @Transactional
    public void reply(String disputeNo, String operator, String content) {
        Dispute d = require(disputeNo);
        reply(d, operator + "（客服）", content);
    }

    /** 代查（R37）：骨架返回该租户账单概览指针；每次调用由调用方记录审计四要素 */
    public List<Dispute> proxyScope(String disputeNo) {
        return List.of(require(disputeNo));
    }

    /** 补偿（R38 审批制）：退费→调整单（审核员初审+财务复审）；送额度→赠送额度单（审核员初审+运营复审） */
    @Transactional
    public Dispute compensate(String disputeNo, String type, long amountCents, String note, String operator) {
        Dispute d = require(disputeNo);
        boolean refund = "refund".equals(type);
        AdjustmentOrder a = new AdjustmentOrder();
        a.setAdjNo("ADJ" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        a.setAdjType(refund ? "adjust_red" : "grant");
        a.setRelatedType("dispute"); a.setRelatedNo(d.getDisputeNo());
        a.setTargetBill(refund ? d.getBillNo() : null);
        a.setAmountCents(BigDecimal.valueOf(refund ? -amountCents : amountCents));
        a.setReason(note == null || note.isBlank() ? "争议核验后补偿" : note);
        a.setInitiator(operator + "（客服发起）");
        a.setStage("review1"); a.setCreatedAt(LocalDateTime.now());
        adjustmentMapper.insert(a);
        d.setStatus("solved");
        d.setCompensation((refund ? "退费调整单 " : "赠送额度单 ") + a.getAdjNo() + "（审批中）");
        disputeMapper.updateById(d);
        try {
            notifyClient.notify(Map.of("tenantId", d.getTenantId(), "type", "success",
                    "title", "争议已解决", "content", "工单 " + d.getDisputeNo() + " 已结案，" + d.getCompensation()));
        } catch (Exception ignored) { }
        reply(d, operator + "（客服）", "已发起补偿（审批制），工单结案。");
        return d;
    }

    @Transactional
    public Dispute close(String disputeNo, String operator) {
        Dispute d = require(disputeNo);
        d.setStatus("solved");
        d.setCompensation("无（口径说明结案）");
        disputeMapper.updateById(d);
        reply(d, operator + "（客服）", "经核验无差错，工单结案。");
        return d;
    }

    private Dispute require(String disputeNo) {
        Dispute d = disputeMapper.selectOne(new LambdaQueryWrapper<Dispute>()
                .eq(Dispute::getDisputeNo, disputeNo).last("limit 1"));
        if (d == null) throw new BusinessException("FIN-4001", "工单不存在");
        return d;
    }

    private void reply(Dispute d, String by, String text) {
        DisputeReply r = new DisputeReply();
        r.setDisputeId(d.getId()); r.setReplier(by); r.setContent(text);
        r.setCreatedAt(LocalDateTime.now());
        replyMapper.insert(r);
    }
}
