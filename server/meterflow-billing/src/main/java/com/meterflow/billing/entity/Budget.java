package com.meterflow.billing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 租户月度预算与告警标记（uk(tenant,period)；标记位防止告警重复发送） */
@Data
@TableName("budget")
public class Budget {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String period;
    private Long budgetCents;
    private Boolean notified80;
    private Boolean notified100;
    private Integer warnRatio;
    private Integer critRatio;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
