package com.meterflow.billing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meterflow.billing.entity.PackageInstance;
import com.meterflow.billing.feign.EquityClient;
import com.meterflow.billing.mapper.PackageInstanceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/** 实例发放（S4）：Feign 与 MQ 消费共用同一入口；幂等键=orderNo（R34） */
@Service
@RequiredArgsConstructor
@Slf4j
public class GrantService {

    private final PackageInstanceMapper packageMapper;
    private final EquityClient equityClient;

    @Transactional
    public Map<String, Object> grant(Map<String, Object> req) {
        String orderNo = String.valueOf(req.get("orderNo"));
        Long exists = packageMapper.selectCount(new LambdaQueryWrapper<PackageInstance>()
                .eq(PackageInstance::getSourceOrderNo, orderNo));
        if (exists > 0) return Map.of("granted", false, "reason", "已发放（幂等跳过）");
        Long goodsId = Long.valueOf(String.valueOf(req.get("goodsId")));
        Map<String, Object> goodsResp = null;
        for (int attempt = 0; attempt < 2; attempt++) {   // 冷连接重试一次
            try {
                goodsResp = equityClient.goods(goodsId);
                break;
            } catch (Exception e) {
                if (attempt == 1) throw e;
                try { Thread.sleep(300); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        }
        @SuppressWarnings("unchecked") Map<String, Object> goods = (Map<String, Object>) goodsResp.get("data");
        // 来源商品已删除（测试清理/商品下架）：业务性跳过，不做无限重试（与 outbox failed 终态同构）
        if (goods == null) {
            log.warn("[GRANT] 来源商品 {} 已删除，跳过发放（消息确认消费）", goodsId);
            return Map.of("granted", false, "reason", "来源商品已删除，跳过");
        }
        PackageInstance p = new PackageInstance();
        p.setTenantId(Long.valueOf(String.valueOf(req.get("tenantId")))); p.setPkgType("spec");
        p.setTitle(String.valueOf(goods.get("name")));
        p.setProductId(Long.valueOf(String.valueOf(goods.get("productId"))));
        p.setMetricKey(String.valueOf(goods.get("metricKey")));
        p.setTotal(new BigDecimal(String.valueOf(goods.get("quantity"))));
        p.setRemaining(p.getTotal()); p.setUnit(String.valueOf(goods.get("unit")));
        p.setExpireDate(LocalDate.now().plusDays(Long.parseLong(String.valueOf(goods.get("validDays")))));
        p.setSourceOrderNo(orderNo); p.setStatus("active");
        packageMapper.insert(p);
        log.info("[GRANT] 实例发放 orderNo={} tenantId={} total={}", orderNo, p.getTenantId(), p.getTotal());
        return Map.of("granted", true, "instanceId", p.getId(), "title", p.getTitle());
    }
}
