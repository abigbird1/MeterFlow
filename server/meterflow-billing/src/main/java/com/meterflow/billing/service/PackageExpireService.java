package com.meterflow.billing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meterflow.billing.entity.PackageInstance;
import com.meterflow.billing.feign.PlatformClient;
import com.meterflow.billing.mapper.PackageInstanceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** 资源包到期提醒与过期转态（R33 生命周期收口）：每日 00:30 扫描，标记位防重复提醒 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PackageExpireService {

    private final PackageInstanceMapper packageMapper;
    private final PlatformClient platformClient;

    /**
     * 扫描：① 7 天内到期且未提醒 → 提醒 + remind7；② 3 天内到期且未提醒 → 提醒 + remind3；
     * ③ 已过期仍 active 的实例 → 转态 expired（到期清零 R33 语义由抵扣时的余量校验兜底）。
     * 返回发送的提醒条数。
     */
    @Transactional
    public int scan() {
        LocalDate today = LocalDate.now();
        int notified = 0;
        notified += remindBatch(today.plusDays(7), today, "资源包 7 天内到期", 7, false);
        notified += remindBatch(today.plusDays(3), today, "资源包 3 天内到期", 3, true);
        // 余量不足提醒（≤10% 且未提醒）：耗尽预警（R33 生命周期）
        List<PackageInstance> lowList = packageMapper.selectList(new LambdaQueryWrapper<PackageInstance>()
                .eq(PackageInstance::getStatus, "active")
                .eq(PackageInstance::getLowNotified, false)
                .apply("total > 0 AND remaining <= total * 0.1"));
        for (PackageInstance p : lowList) {
            notify(p.getTenantId(), "资源包余量不足",
                    String.format("资源包「%s」余量仅剩 %s %s（≤10%%），请关注用量或补充资源包", p.getTitle(), p.getRemaining(), p.getUnit()));
            p.setLowNotified(true);
            packageMapper.updateById(p);
            notified++;
        }
        // 过期转态（R33：到期清零，实例转 expired 保留留痕）
        List<PackageInstance> expired = packageMapper.selectList(new LambdaQueryWrapper<PackageInstance>()
                .eq(PackageInstance::getStatus, "active")
                .lt(PackageInstance::getExpireDate, today));
        for (PackageInstance p : expired) {
            p.setStatus("expired");
            packageMapper.updateById(p);
        }
        if (!expired.isEmpty()) log.info("[PKG-EXPIRE] 过期实例转态 {} 条", expired.size());
        return notified;
    }

    /** 到期窗口 [today, limitDate] 的 active 实例提醒；strict3=true 时只处理未发过 3 天提醒的 */
    private int remindBatch(LocalDate limitDate, LocalDate today, String title, int days, boolean strict3) {
        List<PackageInstance> list = packageMapper.selectList(new LambdaQueryWrapper<PackageInstance>()
                .eq(PackageInstance::getStatus, "active")
                .ge(PackageInstance::getExpireDate, today)
                .le(PackageInstance::getExpireDate, limitDate));
        int sent = 0;
        for (PackageInstance p : list) {
            if (days == 7 && Boolean.TRUE.equals(p.getRemind7())) continue;
            if (days == 3 && Boolean.TRUE.equals(p.getRemind3())) continue;
            notify(p.getTenantId(), title,
                    String.format("资源包「%s」余量 %s %s，将于 %s 到期（%d 天内），到期清零请及时使用（R33）",
                            p.getTitle(), p.getRemaining().stripTrailingZeros().toPlainString(),
                            p.getUnit(), p.getExpireDate(), days));
            if (days == 7) { p.setRemind7(true); } else { p.setRemind3(true); }
            packageMapper.updateById(p);
            sent++;
        }
        return sent;
    }

    private void notify(Long tenantId, String title, String content) {
        try {
            platformClient.notify(java.util.Map.of("tenantId", tenantId, "type", "warn",
                    "title", title, "content", content));
        } catch (Exception e) {
            log.warn("[PKG-EXPIRE] 通知触达失败 tenantId={}：{}", tenantId, e.getMessage());
        }
    }
}
