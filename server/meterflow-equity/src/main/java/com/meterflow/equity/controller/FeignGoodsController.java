package com.meterflow.equity.controller;

import com.meterflow.common.api.Results;
import com.meterflow.common.api.Results.Result;
import com.meterflow.equity.entity.Goods;
import com.meterflow.equity.mapper.GoodsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** goods 只读详情（billing S4 方案 B 回查） */
@RestController
@RequestMapping("/feign/goods")
@RequiredArgsConstructor
public class FeignGoodsController {

    private final GoodsMapper goodsMapper;

    @GetMapping("/{goodsId}")
    public Result<Map<String, Object>> goods(@PathVariable Long goodsId) {
        Goods g = goodsMapper.selectById(goodsId);
        if (g == null) return Results.fail("EQY-0404", "商品不存在");
        return Results.ok(Map.of(
                "goodsId", g.getId(), "name", g.getName(), "goodsType", g.getGoodsType(),
                "productId", g.getProductId() == null ? 0 : g.getProductId(),
                "metricKey", g.getMetricKey() == null ? "" : g.getMetricKey(),
                "quantity", g.getQuantity() == null ? 0 : g.getQuantity(),
                "unit", g.getUnit() == null ? "Token" : g.getUnit(),
                "validDays", g.getValidDays() == null ? 90 : g.getValidDays()));
    }
}
