package com.meterflow.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meterflow.finance.entity.AdjustmentOrder;
import com.meterflow.finance.entity.DailyRecon;
import com.meterflow.finance.entity.ReconError;
import com.meterflow.finance.feign.BillingClient;
import com.meterflow.finance.mapper.AdjustmentOrderMapper;
import com.meterflow.finance.mapper.DailyReconMapper;
import com.meterflow.finance.mapper.ReconErrorMapper;
import com.meterflow.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 对账与冲正（M7）：每日对账（R26）→ 差错（R27）→ 调整单两级审批（R28/R29/I6）→ 生效回写 */
@Service
@RequiredArgsConstructor
public class ReconService {

    private final DailyReconMapper dailyReconMapper;
    private final ReconErrorMapper errorMapper;
    private final AdjustmentOrderMapper adjustmentMapper;
    private final BillingClient billingClient;

    public List<DailyRecon> daily() {
        return dailyReconMapper.selectList(new LambdaQueryWrapper<DailyRecon>()
                .orderByDesc(DailyRecon::getReconDate));
    }

    /** 每日对账（R26 日口径）：对上一业务日三口径比对，写 daily_recon 并生成差错单 */
    @Transactional
    public DailyRecon run(LocalDate date) {
        if (dailyReconMapper.selectCount(new LambdaQueryWrapper<DailyRecon>()
                .eq(DailyRecon::getReconDate, date)) > 0) {
            throw new BusinessException("FIN-4005", date + " 已对账（幂等 R42）");
        }
        long[] diff = compare(billingClient.reconBundle(date.toString(), "").get("data"),
                "REC-" + date.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + "-", date);
        DailyRecon r = new DailyRecon();
        r.setReconDate(date);
        r.setEventCount(reconEventCount);
        r.setDiffAb((int) diff[0]); r.setDiffBc((int) diff[1]);
        r.setResult(diff[0] + diff[1] == 0 ? "ok" : "diff");
        r.setCreatedAt(LocalDateTime.now());
        dailyReconMapper.insert(r);
        return r;
    }

    /** 月末全量复核（R26 月口径）：月末出账后对整个账期复核；未处理差错重建，在审/已生效保留 */
    @Transactional
    public Map<String, Object> runPeriod(String period) {
        LocalDate start = java.time.YearMonth.parse(period).atDay(1);
        String prefix = "RECP-" + period + "-";
        // 仅清理仍未处理的复核差错，避免动到已进入冲正流程的差错单
        errorMapper.delete(new LambdaQueryWrapper<ReconError>()
                .likeRight(ReconError::getErrNo, prefix)
                .eq(ReconError::getStatus, "todo"));
        long[] diff = compare(billingClient.reconBundle(start.toString(), start.plusMonths(1).minusDays(1).toString()).get("data"), prefix, start);
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("period", period);
        out.put("diffAb", diff[0]); out.put("diffBc", diff[1]);
        out.put("errors", diff[0] + diff[1]);
        return out;
    }

    /** 三口径比对核（R27 四类差错）；reconEventCount/差错落库按 reconDate 归档 */
    private long reconEventCount;

    private long[] compare(Object bundleData, String errPrefix, LocalDate reconDate) {
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) bundleData;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> events = data.get("events") == null ? List.of() : (List<Map<String, Object>>) data.get("events");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = data.get("records") == null ? List.of() : (List<Map<String, Object>>) data.get("records");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> deducts = data.get("deducts") == null ? List.of() : (List<Map<String, Object>>) data.get("deducts");
        reconEventCount = events.size();

        // A：eventId -> [tenantId, costCents]
        Map<Long, long[]> aMap = new java.util.HashMap<>();
        for (Map<String, Object> e : events) {
            long id = Long.parseLong(String.valueOf(e.get("id")));
            aMap.put(id, new long[]{Long.parseLong(String.valueOf(e.get("tenantId"))), cents(e.get("costAmount"))});
        }
        // B：eventId -> [tenantId, costCents, walletCents]；同事件多条 → 重复计费
        Map<Long, long[]> bMap = new java.util.HashMap<>();
        List<long[]> dupRecords = new java.util.ArrayList<>();
        for (Map<String, Object> r : records) {
            long eventId = Long.parseLong(String.valueOf(r.get("eventId")));
            long[] row = {Long.parseLong(String.valueOf(r.get("tenantId"))),
                    Long.parseLong(String.valueOf(r.get("costCents"))),
                    Long.parseLong(String.valueOf(r.get("walletCents")))};
            if (bMap.putIfAbsent(eventId, row) != null) dupRecords.add(row);
        }
        // C：eventId -> walletCents
        Map<Long, Long> cMap = new java.util.HashMap<>();
        for (Map<String, Object> d : deducts) {
            cMap.put(Long.parseLong(String.valueOf(d.get("eventId"))), Long.parseLong(String.valueOf(d.get("walletCents"))));
        }

        int seq = errorMapper.selectCount(new LambdaQueryWrapper<ReconError>()
                .likeRight(ReconError::getErrNo, errPrefix)).intValue();
        long diffAb = 0, diffBc = 0;
        List<ReconError> pending = new java.util.ArrayList<>();
        for (var en : aMap.entrySet()) {
            long eventId = en.getKey(); long[] a = en.getValue();
            long[] b = bMap.get(eventId);
            if (b == null) {
                pending.add(err(errPrefix, ++seq, a[0], reconDate, "count_mismatch", "丢事件：事件 " + eventId + " 无计费流水", a[1]));
            } else if (a[1] != b[1]) {
                pending.add(err(errPrefix, ++seq, a[0], reconDate, "amount_mismatch", "抵扣不符：事件 " + eventId + " 计量 " + a[1] + " 分 ≠ 计费 " + b[1] + " 分", Math.abs(a[1] - b[1])));
            }
        }
        for (long[] b : dupRecords) {
            pending.add(err(errPrefix, ++seq, b[0], reconDate, "duplicate", "重复计费：事件对应多条计费流水", b[1]));
        }
        for (var en : bMap.entrySet()) {
            long eventId = en.getKey(); long[] b = en.getValue();
            if (!aMap.containsKey(eventId)) {
                pending.add(err(errPrefix, ++seq, b[0], reconDate, "duplicate", "重复计费：计费流水无对应计量事件 " + eventId, b[1]));
            }
        }
        for (var en : bMap.entrySet()) {
            long eventId = en.getKey(); long[] b = en.getValue();
            long c = cMap.getOrDefault(eventId, 0L);
            if (b[2] != c) {
                pending.add(err(errPrefix, ++seq, b[0], reconDate, "amount_mismatch", "账户不平：事件 " + eventId + " 钱包抵扣 " + b[2] + " 分 ≠ 账户流水 " + c + " 分", Math.abs(b[2] - c)));
            }
        }
        // C 有而 B 无：账户侧存在无计费流水佐证的扣减（反向账户不平）
        for (var en : cMap.entrySet()) {
            long eventId = en.getKey(); long c = en.getValue();
            if (!bMap.containsKey(eventId) && c != 0) {
                long[] a = aMap.get(eventId);
                long tenantId = a != null ? a[0] : 0L;
                pending.add(err(errPrefix, ++seq, tenantId, reconDate, "amount_mismatch",
                        "账户不平：账户扣减流水无对应计费流水（事件 " + eventId + "，扣减 " + c + " 分）", Math.abs(c)));
            }
        }
        for (ReconError e : pending) {
            if (!e.getErrType().equals("amount_mismatch") || e.getDescription().startsWith("抵扣不符")) diffAb++;
            else diffBc++;
            // 幂等落库：同账期/同日 + 同类型 + 同描述的差错不重复生成（月度复核重跑安全）
            if (errorMapper.selectCount(new LambdaQueryWrapper<ReconError>()
                    .eq(ReconError::getReconDate, e.getReconDate())
                    .eq(ReconError::getErrType, e.getErrType())
                    .eq(ReconError::getDescription, e.getDescription())) == 0) {
                errorMapper.insert(e);
            }
        }
        return new long[]{diffAb, diffBc};
    }

    private ReconError err(String prefix, int seq, long tenantId, LocalDate reconDate, String errType, String desc, long impactCents) {
        ReconError e = new ReconError();
        e.setErrNo(prefix + String.format("%02d", seq));
        e.setReconDate(reconDate); e.setTenantId(tenantId); e.setErrType(errType);
        e.setDescription(desc); e.setImpactCents(java.math.BigDecimal.valueOf(impactCents));
        e.setStatus("todo"); e.setCreatedAt(LocalDateTime.now());
        return e;
    }

    private long cents(Object v) {
        return com.meterflow.common.utils.Money.toCents(new java.math.BigDecimal(String.valueOf(v)));
    }

    private ReconError err(LocalDate date, int seq, long tenantId, String errType, String desc, long impactCents) {
        ReconError e = new ReconError();
        e.setErrNo("REC-" + date.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" + String.format("%02d", seq));
        e.setReconDate(date); e.setTenantId(tenantId); e.setErrType(errType);
        e.setDescription(desc); e.setImpactCents(java.math.BigDecimal.valueOf(impactCents));
        e.setStatus("todo"); e.setCreatedAt(LocalDateTime.now());
        return e;
    }

    public List<ReconError> errors() {
        return errorMapper.selectList(new LambdaQueryWrapper<ReconError>()
                .orderByDesc(ReconError::getId));
    }

    public List<AdjustmentOrder> adjustments() {
        return adjustmentMapper.selectList(new LambdaQueryWrapper<AdjustmentOrder>()
                .orderByDesc(AdjustmentOrder::getId));
    }

    /** 发起冲正：差错 todo → correcting；生成调整单待初审（R28） */
    @Transactional
    public AdjustmentOrder createAdjustment(String errNo, String adjType, long amountCents,
                                            String reason, String operator) {
        ReconError e = errorMapper.selectOne(new LambdaQueryWrapper<ReconError>()
                .eq(ReconError::getErrNo, errNo).last("limit 1"));
        if (e == null || !"todo".equals(e.getStatus())) throw new BusinessException("FIN-4001", "差错单不存在或状态不允许");
        AdjustmentOrder a = new AdjustmentOrder();
        a.setAdjNo("ADJ" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        a.setAdjType(adjType);
        a.setRelatedType("error"); a.setRelatedNo(errNo);
        a.setTargetBill(null);
        a.setAmountCents(BigDecimal.valueOf("adjust_red".equals(adjType) ? -amountCents : amountCents));
        a.setReason(reason); a.setInitiator(operator);
        a.setStage("review1"); a.setCreatedAt(LocalDateTime.now());
        adjustmentMapper.insert(a);
        e.setStatus("correcting"); e.setAdjNo(a.getAdjNo());
        errorMapper.updateById(e);
        return a;
    }

    @Transactional
    public void manual(String errNo, String operator) {
        ReconError e = errorMapper.selectOne(new LambdaQueryWrapper<ReconError>()
                .eq(ReconError::getErrNo, errNo).last("limit 1"));
        if (e == null || !"todo".equals(e.getStatus())) throw new BusinessException("FIN-4001", "差错单状态不允许转人工");
        e.setStatus("manual");
        errorMapper.updateById(e);
    }

    /**
     * 两级审批（R29/I6）：review1=审核员/sadmin；review2=财务（grant 类=运营）；初审≠复审。
     * 生效（done）：退费/蓝补 → Feign balance-adjust 回写余额（P3 资金入口）；grant → 赠送额度落地。
     */
    @Transactional
    public AdjustmentOrder review(String adjNo, int stage, boolean pass, String operator, String operatorRole) {
        AdjustmentOrder a = adjustmentMapper.selectOne(new LambdaQueryWrapper<AdjustmentOrder>()
                .eq(AdjustmentOrder::getAdjNo, adjNo).last("limit 1"));
        if (a == null) throw new BusinessException("FIN-4001", "调整单不存在");
        if (stage == 1 && !"review1".equals(a.getStage())) throw new BusinessException("FIN-4001", "调整单不在待初审");
        if (stage == 2 && !"review2".equals(a.getStage())) throw new BusinessException("FIN-4001", "调整单不在待复审");
        boolean isGrant = "grant".equals(a.getAdjType());
        if (stage == 1 && !isReviewer(operatorRole) && !"sadmin".equals(operatorRole)) {
            throw new BusinessException("GWY-0403", "初审岗为审核员");
        }
        if (stage == 2) {
            String need = isGrant ? "ops" : "fin";
            if (!need.equals(operatorRole) && !"sadmin".equals(operatorRole)) {
                throw new BusinessException("GWY-0403", "复审岗为" + (isGrant ? "运营" : "财务"));
            }
            if (a.getReview1By() != null && a.getReview1By().equals(operator)) {
                throw new BusinessException("FIN-4003", "初审与复审不可同人（I6）");
            }
        }
        LocalDateTime now = LocalDateTime.now();
        if (stage == 1) {
            if (pass) { a.setStage("review2"); a.setReview1By(operator); a.setReview1At(now); }
            else { a.setStage("rejected"); a.setReview1By(operator); a.setReview1At(now); rejectError(a); }
        } else {
            if (pass) {
                a.setStage("done"); a.setReview2By(operator); a.setReview2At(now);
                String effect;
                if (isGrant) {
                    effect = "赠送额度落地（见 /a/grants 发放记录）";
                } else {
                    // 调整单金额为"收入冲减/补记"口径，对租户余额方向相反：红冲退费=加钱、蓝补补收=扣钱
                    long tenantDelta = -a.getAmountCents().longValue();
                    var resp = billingClient.balanceAdjust(Map.of(
                            "tenantId", relatedTenant(a),
                            "amountCents", tenantDelta,
                            "refType", "adjustment_order", "refId", a.getId(),
                            "note", a.getReason()));
                    effect = "租户余额变动 " + tenantDelta / 100.0 + " 元 → " + resp.get("data");
                }
                a.setEffectNote(effect);
                markCorrected(a);
            } else {
                a.setStage("rejected"); a.setReview2By(operator); a.setReview2At(now);
                rejectError(a);
            }
        }
        adjustmentMapper.updateById(a);
        return a;
    }

    private Long relatedTenant(AdjustmentOrder a) {
        ReconError e = errorMapper.selectOne(new LambdaQueryWrapper<ReconError>()
                .eq(ReconError::getErrNo, a.getRelatedNo()).last("limit 1"));
        return e == null ? 1001L : e.getTenantId();
    }

    private void markCorrected(AdjustmentOrder a) {
        ReconError e = errorMapper.selectOne(new LambdaQueryWrapper<ReconError>()
                .eq(ReconError::getErrNo, a.getRelatedNo()).last("limit 1"));
        if (e != null) { e.setStatus("corrected"); errorMapper.updateById(e); }
    }

    private void rejectError(AdjustmentOrder a) {
        ReconError e = errorMapper.selectOne(new LambdaQueryWrapper<ReconError>()
                .eq(ReconError::getErrNo, a.getRelatedNo()).last("limit 1"));
        if (e != null && "correcting".equals(e.getStatus())) { e.setStatus("todo"); errorMapper.updateById(e); }
    }

    private boolean isReviewer(String role) { return "reviewer".equals(role); }

    /** D5：转人工处置完成（人工核验后关闭差错单） */
    @Transactional
    public void closeManual(String errNo, String operator) {
        ReconError e = errorMapper.selectOne(new LambdaQueryWrapper<ReconError>()
                .eq(ReconError::getErrNo, errNo).last("limit 1"));
        if (e == null || !"manual".equals(e.getStatus())) throw new BusinessException("FIN-4001", "差错单不在转人工状态");
        e.setStatus("closed");
        errorMapper.updateById(e);
    }

}
