package com.meterflow.billing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/** 内置免费额度（租户×产品×指标×账期，自然月重置不结转 R13） */
@Data
@TableName("free_quota")
public class FreeQuota {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long productId;
    private Long metricId;
    private String period;
    private BigDecimal totalQuota;
    private BigDecimal remaining;
}
