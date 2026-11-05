package com.meterflow.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 差错单（R26–R28） */
@Data
@TableName("recon_error")
public class ReconError {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String errNo;
    private LocalDate reconDate;
    /** 丢事件/重复计费/抵扣不符/账户不平 */
    private String errType;
    private Long tenantId;
    private String description;
    private BigDecimal impactCents;
    /** todo/correcting/corrected/manual/closed */
    private String status;
    private String adjNo;
    private LocalDateTime createdAt;
}
