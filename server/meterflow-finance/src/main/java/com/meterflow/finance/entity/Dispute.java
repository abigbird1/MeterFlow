package com.meterflow.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 争议工单（R35–R38、R44） */
@Data
@TableName("dispute")
public class Dispute {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String disputeNo;
    private Long tenantId;
    private String billNo;
    private BigDecimal amountCents;
    private String reason;
    /** pending/processing/solved/closed */
    private String status;
    private Integer slaHours;
    private LocalDateTime slaDeadline;
    private Boolean isEscalated;
    private String compensation;
    private LocalDateTime createdAt;
}
