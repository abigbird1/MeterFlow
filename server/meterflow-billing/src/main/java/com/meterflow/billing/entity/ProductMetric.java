package com.meterflow.billing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/** 计量指标（R10：阶梯档位 JSON；内置免费额度随指标定义） */
@Data
@TableName("product_metric")
public class ProductMetric {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long productId;
    /** calls/in_tok/out_tok/storage */
    private String metricKey;
    private String name;
    /** 次/千Token/GB·月 */
    private String unit;
    /** seg 累计分段 / flat 档位单价 */
    private String tierMode;
    /** 阶梯档位 JSON [{from,to,price}] */
    private String tierJson;
    private BigDecimal freeQuota;
    private String freeUnit;
}
