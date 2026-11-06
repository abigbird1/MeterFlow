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

    @Scheduled(cron = "0 10 * * * ?")
    public void budgetAlertScan() {
        int n = budgetService.checkAndNotify();
        if (n > 0) log.info("[TASK] 月度预算告警 {} 条", n);
    }
}
