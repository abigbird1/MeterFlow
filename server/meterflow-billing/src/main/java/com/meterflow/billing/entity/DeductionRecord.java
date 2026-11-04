package com.meterflow.billing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 抵扣主流水（D2 两级结构；append-only；分表逻辑表） */
@Data
@TableName("deduction_record")
public class DeductionRecord {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long eventId;
    private Long productId;
    private BigDecimal costAmount;
    /** 抵扣链快照 JSON */
    private String chainJson;
    private LocalDateTime createdAt;
}
