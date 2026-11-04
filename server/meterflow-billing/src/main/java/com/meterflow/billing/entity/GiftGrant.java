package com.meterflow.billing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 赠送额度（独立权益；先到期先扣 A2） */
@Data
@TableName("gift_grant")
public class GiftGrant {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String grantNo;
    private Long tenantId;
    private Long productId;
    private Long metricId;
    private String name;
    private BigDecimal total;
    private BigDecimal remaining;
    private LocalDate expireDate;
    /** ops/dispute */
    private String source;
    private String status;
}
