package com.meterflow.billing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/** 版本单价（元/计价单位，最小 0.0001）；tier_json 版本化阶梯价（04 v1.4，NULL 回退指标配置） */
@Data
@TableName("price_item")
public class PriceItem {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long versionId;
    private Long metricId;
    private BigDecimal price;
    private String tierJson;
}
