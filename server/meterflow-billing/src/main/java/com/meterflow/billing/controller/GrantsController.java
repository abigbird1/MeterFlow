package com.meterflow.billing.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meterflow.billing.entity.GiftGrant;
import com.meterflow.billing.entity.ProductMetric;
import com.meterflow.billing.mapper.GiftGrantMapper;
import com.meterflow.billing.mapper.ProductMetricMapper;
import com.meterflow.common.api.Results;
import com.meterflow.common.api.Results.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 赠送额度管理（05 §6.6.1/6.6.2，运营发放 + 审计留痕 A5） */
@RestController
@RequestMapping("/a/grants")
@RequiredArgsConstructor
public class GrantsController {

    private final GiftGrantMapper giftGrantMapper;
    private final ProductMetricMapper metricMapper;

    public record GrantRequest(String tenantId, String productMetric, BigDecimal amount, String expireDate) {}

    @GetMapping
    public Result<List<GiftGrant>> list() {
        return Results.ok(giftGrantMapper.selectList(new LambdaQueryWrapper<GiftGrant>()
                .orderByDesc(GiftGrant::getId)));
    }

    /** 发放（运营直接发放 + 审计留痕；productMetric 形如 "1001:1103"） */
    @PostMapping
    public Result<GiftGrant> grant(@RequestBody GrantRequest req) {
        String[] parts = req.productMetric().split(":");
        Long productId = Long.valueOf(parts[0]);
        Long metricId = Long.valueOf(parts[1]);
        ProductMetric metric = metricMapper.selectById(metricId);
        GiftGrant g = new GiftGrant();
        g.setGrantNo("GT" + System.currentTimeMillis());
        g.setTenantId(Long.valueOf(req.tenantId()));
        g.setProductId(productId); g.setMetricId(metricId);
        g.setName("运营赠送");
        g.setTotal(req.amount()); g.setRemaining(req.amount());
        g.setExpireDate(LocalDate.parse(req.expireDate()));
        g.setSource("ops"); g.setStatus("active");
        giftGrantMapper.insert(g);
        return Results.ok(g);
    }
}
