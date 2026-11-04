package com.meterflow.billing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 日切快照（R18：定案不回溯） */
@Data
@TableName("daily_snapshot")
public class DailySnapshot {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private LocalDate snapshotDate;
    private Long tenantId;
    private Long eventCount;
    private String usageJson;
    private BigDecimal costCents;
    private String status;
}
