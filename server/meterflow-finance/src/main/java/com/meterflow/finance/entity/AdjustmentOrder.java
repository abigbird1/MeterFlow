package com.meterflow.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 调整单（红冲/蓝补/赠送额度单；两级审批 R29，初审≠复审 I6；生效回写 R21） */
@Data
@TableName("adjustment_order")
public class AdjustmentOrder {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String adjNo;
    /** adjust_red/adjust_blue/grant */
    private String adjType;
    /** error/dispute */
    private String relatedType;
    private String relatedNo;
    private String targetBill;
    private BigDecimal amountCents;
    private String reason;
    private String initiator;
    /** review1/review2/done/rejected */
    private String stage;
    private String review1By;
    private LocalDateTime review1At;
    private String review2By;
    private LocalDateTime review2At;
    private String effectNote;
    private LocalDateTime createdAt;
}
