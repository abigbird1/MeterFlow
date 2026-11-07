package com.meterflow.finance.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/** billing 内部只读/回写（05 §7.1 扩展）：欠费清单 / 月度汇总 / 调整单生效回写 */
@FeignClient(name = "meterflow-billing", contextId = "billingClient")
public interface BillingClient {

    @GetMapping("/feign/billing/daily-summary/{tenantId}/{date}")
    Map<String, Object> dailySummary(@PathVariable("tenantId") Long tenantId,
                                     @PathVariable("date") String date);

    @GetMapping("/feign/billing/month-dept-sub/{tenantId}/{period}")
    Map<String, Object> monthDeptSub(@PathVariable("tenantId") Long tenantId,
                                     @PathVariable("period") String period);

    @GetMapping("/feign/billing/accounts/debts")
    Map<String, Object> debts();

    @GetMapping("/feign/billing/accounts/{tenantId}")
    Map<String, Object> account(@PathVariable("tenantId") Long tenantId);

    @GetMapping("/feign/billing/month-summary/{tenantId}/{period}")
    Map<String, Object> monthSummary(@PathVariable("tenantId") Long tenantId,
                                     @PathVariable("period") String period);

    /** 三口径对账数据束（R26）：A 计量 / B 计费(含钱包部分) / C 账户扣减；endDate 传区间（全量复核） */
    @GetMapping("/feign/billing/recon-bundle/{date}")
    Map<String, Object> reconBundle(@PathVariable("date") String date,
                                    @org.springframework.web.bind.annotation.RequestParam("endDate") String endDate);

    /** 指定账期有用量事件的租户清单（月末全员出账 R17） */
    @GetMapping("/feign/billing/active-tenants/{period}")
    Map<String, Object> activeTenants(@PathVariable("period") String period);

    @PostMapping("/feign/billing/balance-adjust")
    Map<String, Object> balanceAdjust(@RequestBody Map<String, Object> payload);

    /** 站内通知触达（A3/D6：经 platform /feign/notify 落库） */
    @org.springframework.web.bind.annotation.PostMapping("/feign/notify")
    Map<String, Object> notify(@org.springframework.web.bind.annotation.RequestBody Map<String, Object> payload);



    /** 赠送额度单生效后的落地发放（复用 billing /a/grants 内部能力） */
    @PostMapping("/a/grants")
    Map<String, Object> grant(@RequestBody Map<String, Object> payload);
}
