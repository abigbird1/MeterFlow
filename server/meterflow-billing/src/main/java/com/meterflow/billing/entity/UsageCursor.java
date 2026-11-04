package com.meterflow.billing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/** 阶梯累计游标（租户×产品×指标×账期，04 v1.3；R10 分段计价） */
@Data
@TableName("usage_cursor")
public class UsageCursor {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long productId;
    private Long metricId;
    private String period;
    private BigDecimal qty;
}
