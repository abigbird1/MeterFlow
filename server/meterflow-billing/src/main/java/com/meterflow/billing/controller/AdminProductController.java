package com.meterflow.billing.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meterflow.billing.entity.Product;
import com.meterflow.billing.entity.ProductMetric;
import com.meterflow.billing.entity.PriceVersion;
import com.meterflow.billing.mapper.ProductMapper;
import com.meterflow.billing.mapper.ProductMetricMapper;
import com.meterflow.billing.mapper.PriceVersionMapper;
import com.meterflow.common.api.Results;
import com.meterflow.common.api.Results.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.meterflow.billing.feign.IdentityClient;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 管理端：产品目录与计价（05 §6.2.1~6.2.3，R11 调价即时生效） */
@RestController
@RequestMapping("/a/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductMapper productMapper;
    private final ProductMetricMapper metricMapper;
    private final PriceVersionMapper versionMapper;
    private final com.meterflow.billing.mapper.PriceItemMapper priceItemMapper;
    private final IdentityClient identityClient;

    public record PriceVersionRequest(java.util.List<Item> items, String note, String operator) {
        public record Item(Long metricId, java.math.BigDecimal price) {}
    }

    @GetMapping
    public Result<List<Product>> list() {
        return Results.ok(productMapper.selectList(new LambdaQueryWrapper<Product>().orderByAsc(Product::getId)));
    }

    @GetMapping("/{productId}")
    public Result<Map<String, Object>> detail(@PathVariable Long productId) {
        Product p = productMapper.selectById(productId);
        List<ProductMetric> metrics = metricMapper.selectList(new LambdaQueryWrapper<ProductMetric>()
                .eq(ProductMetric::getProductId, productId));
        List<PriceVersion> versions = versionMapper.selectList(new LambdaQueryWrapper<PriceVersion>()
                .eq(PriceVersion::getProductId, productId).orderByDesc(PriceVersion::getEffectiveAt));
        // 各版本的单价明细（版本 × 指标），前端按 versionId 联表展示
        List<com.meterflow.billing.entity.PriceItem> items = versions.isEmpty()
                ? List.of()
                : priceItemMapper.selectList(new LambdaQueryWrapper<com.meterflow.billing.entity.PriceItem>()
                        .in(com.meterflow.billing.entity.PriceItem::getVersionId,
                                versions.stream().map(PriceVersion::getId).toList()));
        Map<String, Object> out = new HashMap<>();
        out.put("product", p);
        out.put("metrics", metrics);
        out.put("versions", versions);
        out.put("items", items);
        return Results.ok(out);
    }

    /** 调价：新版本即时生效（R11），审计由调用方留痕 */
    @PostMapping("/{productId}/price-versions")
    /** A5：调价岗校验（6.2 矩阵：运营/管理员） */
    private void requireOps(String authorization) {
        Map<String, Object> me = null;
        try {
            me = identityClient.me(authorization);
        } catch (Exception e) { }
        @SuppressWarnings("unchecked")
        Map<String, Object> data = me == null ? null : (Map<String, Object>) me.get("data");
        String role = data == null ? "" : String.valueOf(data.get("role"));
        if (!"ops".equals(role) && !"sadmin".equals(role)) {
            throw new com.meterflow.common.exception.BusinessException("GWY-0403", "调价岗为运营/管理员");
        }
    }

    public Result<PriceVersion> newVersion(@PathVariable Long productId,
                                           @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authorization,
                                           @RequestBody PriceVersionRequest req) {
        requireOps(authorization);
        int nextNo = versionMapper.selectList(new LambdaQueryWrapper<PriceVersion>()
                .eq(PriceVersion::getProductId, productId))
                .stream().mapToInt(PriceVersion::getVersionNo).max().orElse(0) + 1;
        PriceVersion v = new PriceVersion();
        v.setProductId(productId); v.setVersionNo(nextNo);
        v.setEffectiveAt(LocalDateTime.now()); v.setNote(req.note());
        v.setCreatedBy(req.operator() == null ? "运营" : req.operator());
        versionMapper.insert(v);
        return Results.ok(v);
    }
}
