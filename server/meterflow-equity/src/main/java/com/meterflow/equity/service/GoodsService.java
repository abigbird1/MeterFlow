package com.meterflow.equity.service;

import com.meterflow.equity.dto.GoodsRequest;
import com.meterflow.equity.entity.Goods;
import com.meterflow.equity.mapper.GoodsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/** 商品管理（M9 运营侧：上下架/三重限制/窗口） */
@Service
@RequiredArgsConstructor
public class GoodsService {

    private final GoodsMapper goodsMapper;

    public List<Goods> list() {
        return goodsMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Goods>()
                .orderByDesc(Goods::getId));
    }

    public Goods create(GoodsRequest req) {
        Goods g = from(req, new Goods());
        g.setSold(0);
        g.setStatus("on");
        goodsMapper.insert(g);
        return g;
    }

    public Goods update(Long id, GoodsRequest req) {
        Goods g = goodsMapper.selectById(id);
        if (g == null) return null;
        Goods updated = from(req, g);
        goodsMapper.updateById(updated);
        return updated;
    }

    public Goods changeStatus(Long id, String status) {
        Goods g = goodsMapper.selectById(id);
        g.setStatus(status);
        goodsMapper.updateById(g);
        return g;
    }

    private Goods from(GoodsRequest req, Goods g) {
        g.setGoodsType(req.goodsType()); g.setName(req.name()); g.setSpec(req.spec());
        g.setProductId(req.productId()); g.setMetricKey(req.metricKey());
        g.setFaceAmountCents(req.faceAmountCents()); g.setPriceCents(req.priceCents());
        g.setValidDays(req.validDays()); g.setQuantity(req.quantity()); g.setUnit(req.unit());
        g.setTotalStock(req.totalStock());
        g.setPerLimit(req.perLimit()); g.setOrderLimit(req.orderLimit());
        g.setWindowStart(LocalDate.parse(req.windowStart()));
        g.setWindowEnd(LocalDate.parse(req.windowEnd()));
        return g;
    }
}
