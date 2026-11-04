package com.meterflow.equity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

/** 资源包商品（R30/R31：三重限制；库存权威值，Redis 预扣为热路径） */
@Data
@TableName("goods")
public class Goods {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** spec/common */
    private String goodsType;
    private String name;
    private String spec;
    private Long productId;
    private String metricKey;
    /** common：面额（分） */
    private Long faceAmountCents;
    private Long priceCents;
    private Integer validDays;
    private java.math.BigDecimal quantity;
    private String unit;
    private Integer totalStock;
    private Integer sold;
    private Integer perLimit;
    private Integer orderLimit;
    private LocalDate windowStart;
    private LocalDate windowEnd;
    /** on/off */
    private String status;
}
