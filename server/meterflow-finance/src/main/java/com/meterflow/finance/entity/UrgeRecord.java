package com.meterflow.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 催缴记录（R25：全停才催缴，留痕） */
@Data
@TableName("urge_record")
public class UrgeRecord {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private BigDecimal balanceCents;
    private String detail;
    private LocalDateTime createdAt;
}
