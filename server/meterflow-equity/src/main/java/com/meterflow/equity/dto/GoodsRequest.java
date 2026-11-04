package com.meterflow.equity.dto;

/** 商品创建/编辑入参（R30/R31） */
public record GoodsRequest(String goodsType, String name, String spec, Long productId,
                           String metricKey, Long faceAmountCents, Long priceCents,
                           Integer validDays, java.math.BigDecimal quantity, String unit, Integer totalStock, Integer perLimit,
                           Integer orderLimit, String windowStart, String windowEnd) {}
