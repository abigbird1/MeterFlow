package com.meterflow.billing.task;

import com.meterflow.billing.service.BudgetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** billing 定时任务：月度预算告警扫描（每小时第 10 分；标记位防重复发送） */
@Component
@RequiredArgsConstructor
@Slf4j
public class BillingTaskScheduler {

    private final BudgetService budgetService;
    private final com.meterflow.billing.service.AnomalyService anomalyService;
    private final com.meterflow.billing.service.PackageExpireService packageExpireService;

    @Scheduled(cron = "0 10 * * * ?")
    public void budgetAlertScan() {
        int n = budgetService.checkAndNotify();
        if (n > 0) log.info("[TASK] 月度预算告警 {} 条", n);
    }

    /** 消费异常检测（每日 00:15，日切对账之后对昨日数据扫描） */
    @Scheduled(cron = "0 15 0 * * ?")
    public void consumptionAnomalyScan() {
        int n = anomalyService.scan(java.time.LocalDate.now().minusDays(1));
        if (n > 0) log.info("[TASK] 消费异常提醒 {} 条", n);
    }

    /** 资源包到期提醒与过期转态（每日 00:30；R33 生命周期收口） */
    @Scheduled(cron = "0 30 0 * * ?")
    public void packageExpireScan() {
        int n = packageExpireService.scan();
        if (n > 0) log.info("[TASK] 资源包到期提醒 {} 条", n);
    }
}
