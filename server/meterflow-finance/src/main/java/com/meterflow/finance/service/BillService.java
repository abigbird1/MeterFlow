package com.meterflow.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meterflow.finance.entity.Bill;
import com.meterflow.finance.entity.BillSection;
import com.meterflow.finance.entity.TaskInstance;
import com.meterflow.finance.feign.BillingClient;
import com.meterflow.finance.mapper.BillMapper;
import com.meterflow.finance.mapper.BillSectionMapper;
import com.meterflow.finance.mapper.TaskInstanceMapper;
import com.meterflow.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 账单域（M6）：出账（R17/R42 幂等）→ 确认 → 结清；数据经 Feign billing 月度汇总 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BillService {

    private final BillMapper billMapper;
    private final BillSectionMapper sectionMapper;
    private final com.meterflow.finance.mapper.BillDeptSubMapper billDeptSubMapper;
    private final TaskInstanceMapper taskMapper;
    private final BillingClient billingClient;
    private final ReconService reconService;
    private final com.meterflow.finance.feign.NotifyClient notifyClient;

    public List<Bill> list() {
        return billMapper.selectList(new LambdaQueryWrapper<Bill>().orderByDesc(Bill::getId));
    }

    public Bill detail(String billNo) {
        return billMapper.selectOne(new LambdaQueryWrapper<Bill>().eq(Bill::getBillNo, billNo).last("limit 1"));
    }

    /** 账单详情聚合（05 §5.4.3：费用构成 + 版本分段 + 部门小计一次返回） */
    public Map<String, Object> detailBundle(String billNo) {
        Bill bill = detail(billNo);
        if (bill == null) throw new BusinessException("FIN-0404", "账单不存在");
        Map<String, Object> bundle = new java.util.LinkedHashMap<>();
        bundle.put("bill", bill);
        bundle.put("sections", sections(bill.getId()));
        bundle.put("deptSubs", billDeptSubMapper.selectList(
                new LambdaQueryWrapper<com.meterflow.finance.entity.BillDeptSub>()
                        .eq(com.meterflow.finance.entity.BillDeptSub::getBillId, bill.getId())));
        return bundle;
    }

    public List<BillSection> sections(Long billId) {
        return sectionMapper.selectList(new LambdaQueryWrapper<BillSection>().eq(BillSection::getBillId, billId));
    }

    public List<Bill> byTenant(Long tenantId) {
        return billMapper.selectList(new LambdaQueryWrapper<Bill>()
                .eq(Bill::getTenantId, tenantId).orderByDesc(Bill::getId));
    }

    /** 月度实时汇总（billing 聚合，本账期进度用） */
    @SuppressWarnings("unchecked")
    public Map<String, Object> monthSummary(Long tenantId, String period) {
        Map<String, Object> resp = billingClient.monthSummary(tenantId, period);
        return resp.get("data") == null ? Map.of() : (Map<String, Object>) resp.get("data");
    }

    /** 部门小计补写（幂等：已有则跳过；数据经 billing month-dept-sub 真实聚合） */
    private void ensureDeptSub(Bill b) {
        if (billDeptSubMapper.selectCount(new LambdaQueryWrapper<com.meterflow.finance.entity.BillDeptSub>()
                .eq(com.meterflow.finance.entity.BillDeptSub::getBillId, b.getId())) > 0) return;
        var resp = billingClient.monthDeptSub(b.getTenantId(), b.getPeriod());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) resp.get("data");
        for (Map<String, Object> row : rows == null ? List.<Map<String, Object>>of() : rows) {
            com.meterflow.finance.entity.BillDeptSub ds = new com.meterflow.finance.entity.BillDeptSub();
            ds.setBillId(b.getId());
            ds.setDeptName(String.valueOf(row.get("deptName")));
            ds.setAmountCents(BigDecimal.valueOf(Long.parseLong(String.valueOf(row.get("amountCents")))));
            ds.setCreatedAt(LocalDateTime.now());
            billDeptSubMapper.insert(ds);
        }
    }

    /** 月末全员出账（R17 闭环）：对账期内有用量事件的全部租户逐户出账；返回摘要 */
    public Map<String, Object> monthlyBillAll(String period) {
        Map<String, Object> resp = billingClient.activeTenants(period);
        @SuppressWarnings("unchecked")
        List<Object> raw = resp.get("data") == null ? List.of() : (List<Object>) resp.get("data");
        List<Long> tenantIds = raw.stream().map(v -> Long.valueOf(String.valueOf(v))).distinct().sorted().toList();
        java.util.List<Map<String, Object>> results = new java.util.ArrayList<>();
        int created = 0, existed = 0, failed = 0;
        for (Long tenantId : tenantIds) {
            try {
                Bill pre = billMapper.selectOne(new LambdaQueryWrapper<Bill>()
                        .eq(Bill::getTenantId, tenantId).eq(Bill::getPeriod, period).last("limit 1"));
                Bill b = monthlyBill(tenantId, period);
                boolean isNew = pre == null;
                if (isNew) created++; else existed++;
                Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("tenantId", tenantId); row.put("billNo", b.getBillNo());
                row.put("payableCents", b.getPayableCents()); row.put("created", isNew);
                results.add(row);
            } catch (Exception e) {
                failed++;
                Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("tenantId", tenantId); row.put("error", e.getMessage());
                results.add(row);
            }
        }
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("period", period); out.put("tenants", tenantIds.size());
        out.put("created", created); out.put("existed", existed); out.put("failed", failed);
        out.put("results", results);
        // R26：月末账单生成后全量复核（三口径），摘要随响应返回
        try {
            out.put("recon", reconService.runPeriod(period));
        } catch (Exception e) {
            out.put("recon", Map.of("errors", -1, "error", String.valueOf(e.getMessage())));
        }
        return out;
    }

    /** 月末出账（R17；幂等 R42：uk(tenant,period)） */
    @Transactional
    public Bill monthlyBill(Long tenantId, String period) {
        String bizKey = tenantId + ":" + period;
        Bill existing = billMapper.selectOne(new LambdaQueryWrapper<Bill>()
                .eq(Bill::getTenantId, tenantId).eq(Bill::getPeriod, period).last("limit 1"));
        if (existing != null) { ensureDeptSub(existing); return existing; }
        Map<String, Object> resp = billingClient.monthSummary(tenantId, period);
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) resp.get("data");
        long costCents = Long.parseLong(String.valueOf(summary.get("costCents")));
        long eventCount = Long.parseLong(String.valueOf(summary.get("eventCount")));

        Bill b = new Bill();
        // 账单号带租户维度（uk_bill_no 全局唯一；B2026-11-1001）
        b.setBillNo("B" + period + "-" + tenantId);
        b.setTenantId(tenantId); b.setPeriod(period);
        b.setOriginalCents(BigDecimal.valueOf(costCents));
        b.setAdjustCents(BigDecimal.ZERO);
        b.setPayableCents(BigDecimal.valueOf(costCents));
        b.setStatus("draft"); b.setIsAdjusted(false); b.setIsDisputed(false);
        b.setCreatedAt(LocalDateTime.now());
        try {
            billMapper.insert(b);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            return billMapper.selectOne(new LambdaQueryWrapper<Bill>()
                    .eq(Bill::getTenantId, tenantId).eq(Bill::getPeriod, period).last("limit 1"));
        }
        // 出账自动通知租户（A3/D6：站内触达，账单生命周期感知）；冷连接失败重试一次
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                notifyClient.notify(Map.of("tenantId", tenantId, "type", "info",
                        "title", "账单已出",
                        "content", String.format("%s 账单 %s 已出账，应付 %s 元，请及时确认与支付（R17）",
                                period, b.getBillNo(), b.getPayableCents().divide(java.math.BigDecimal.valueOf(100)).toPlainString())));
                break;
            } catch (Exception e) {
                if (attempt == 1) log.warn("[BILL] 出账通知触达失败 billNo={}：{}", b.getBillNo(), e.getMessage());
                else try { Thread.sleep(300); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        }
        // 部门小计需在 insert 后补写（雪花 ID 由 insert 时分配，bill_dept_sub 外键依赖）
        ensureDeptSub(b);

        BillSection s = new BillSection();
        s.setBillId(b.getId()); s.setProductId(0L); s.setVersion("ALL");
        s.setRangeStart(LocalDate.of(Integer.parseInt(period.substring(0, 4)), Integer.parseInt(period.substring(5)), 1));
        s.setRangeEnd(s.getRangeStart().plusMonths(1).minusDays(1));
        s.setAmountCents(BigDecimal.valueOf(costCents)); s.setCreatedAt(LocalDateTime.now());
        sectionMapper.insert(s);
        // 部门小计已由上方 ensureDeptSub(b) 统一补写（billing month-dept-sub 真实聚合），此处不再重复插入

        TaskInstance t = new TaskInstance();
        t.setTaskName("monthly_bill"); t.setBizKey(bizKey); t.setStatus("success");
        t.setRetryCount(0); t.setRemark("events=" + eventCount); t.setLastRunAt(LocalDateTime.now());
        t.setCreatedAt(LocalDateTime.now()); t.setUpdatedAt(LocalDateTime.now());
        taskMapper.insert(t);
        return b;
    }

    @Transactional
    public Bill confirm(String billNo) {
        Bill b = require(billNo);
        if (!"draft".equals(b.getStatus())) throw new BusinessException("FIN-4001", "账单状态不允许确认");
        b.setStatus("confirmed"); b.setConfirmedAt(LocalDateTime.now());
        billMapper.updateById(b);
        return b;
    }

    @Transactional
    public Bill settle(String billNo) {
        Bill b = require(billNo);
        if (!"confirmed".equals(b.getStatus())) throw new BusinessException("FIN-4001", "账单状态不允许结清");
        b.setStatus("settled"); b.setSettledAt(LocalDateTime.now());
        billMapper.updateById(b);
        return b;
    }

    private Bill require(String billNo) {
        Bill b = detail(billNo);
        if (b == null) throw new BusinessException("FIN-4001", "账单不存在");
        return b;
    }
}
