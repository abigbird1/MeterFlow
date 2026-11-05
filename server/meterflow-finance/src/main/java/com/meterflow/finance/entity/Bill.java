package com.meterflow.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 账单（R17–R21；uk(tenant,period) 重跑幂等 R42；保留≥3 年） */
@Data
@TableName("bill")
public class Bill {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String billNo;
    private Long tenantId;
    private String period;
    private BigDecimal originalCents;
    private BigDecimal adjustCents;
    private BigDecimal payableCents;
    /** draft/confirmed/settled */
    private String status;
    private Boolean isAdjusted;
    private Boolean isDisputed;
    private LocalDateTime confirmedAt;
    private LocalDateTime settledAt;
    private LocalDateTime createdAt;
}
