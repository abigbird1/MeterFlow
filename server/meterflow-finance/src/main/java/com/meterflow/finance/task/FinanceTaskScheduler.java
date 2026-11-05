package com.meterflow.finance.task;

import com.meterflow.finance.entity.DailyRecon;
import com.meterflow.finance.service.DisputeService;
import com.meterflow.finance.service.ReconService;
import com.meterflow.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Map;
import org.springframework.stereotype.Component;

/** 财务定时任务（XXL-Job 执行器接入前的 Spring @Scheduled 骨架）：
 *  - 日切对账：每日 00:05（账期规则/R26），run() 按日幂等（FIN-4005）
 *  - 月末全员出账：每月最后一天 23:30（R17 闭环），按 uk(tenant,period) 幂等（R42）
 *  - 争议 SLA 升级：每 5 分钟扫描超期工单（R36）
 */
@Component
@RequiredArgsConstructor
public class FinanceTaskScheduler {

    private static final Logger log = LoggerFactory.getLogger(FinanceTaskScheduler.class);

    private final ReconService reconService;
    private final DisputeService disputeService;
    private final com.meterflow.finance.service.BillService billService;

    /** 日切对账（每日 00:05） */
    @Scheduled(cron = "0 5 0 * * ?")
    public void dailyCut() {
        try {
            DailyRecon r = reconService.run(java.time.LocalDate.now().minusDays(1));   // 日切：对上一业务日（00:05 截断）
            log.info("[TASK] 日切对账完成 date={} result={}", r.getReconDate(), r.getResult());
        } catch (BusinessException e) {
            log.info("[TASK] 日切对账跳过：{}", e.getMessage());
        } catch (Exception e) {
            log.warn("[TASK] 日切对账失败：{}", e.getMessage());
        }
    }

    /** 月末全员出账（每月 28~31 日 23:30 触发，仅当次日为 1 日即月末执行；Spring cron 不支持 L） */
    @Scheduled(cron = "0 30 23 28-31 * ?")
    public void monthEndBilling() {
        if (java.time.LocalDate.now().plusDays(1).getDayOfMonth() != 1) return;
        String period = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
        try {
            Map<String, Object> r = billService.monthlyBillAll(period);
            log.info("[TASK] 月末全员出账完成 period={} tenants={} created={} existed={} failed={}",
                    r.get("period"), r.get("tenants"), r.get("created"), r.get("existed"), r.get("failed"));
        } catch (Exception e) {
            log.warn("[TASK] 月末全员出账失败 period={}：{}", period, e.getMessage());
        }
    }

    /** 争议 SLA 超时升级（每 5 分钟；启动后 15 秒首跑便于演示验证） */
    @Scheduled(fixedDelay = 300_000, initialDelay = 15_000)
    public void disputeSlaEscalation() {
        try {
            int n = disputeService.escalateOverdue();
            if (n > 0) log.info("[TASK] 争议 SLA 自动升级 {} 单（R36）", n);
        } catch (Exception e) {
            log.warn("[TASK] 争议 SLA 升级失败：{}", e.getMessage());
        }
    }
}
