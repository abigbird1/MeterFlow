package com.meterflow.billing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 用量事件（复合事件 R1；append-only P2；到达时间计费 R5；分表逻辑表） */
@Data
@TableName("usage_event")
public class UsageEvent {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String ingestSystem;
    private String eventId;
    private Long tenantId;
    private Long deptId;
    private String deptName;
    private Long productId;
    /** 指标集 JSON {key:原生单位量} */
    private String metrics;
    private String bizNo;
    /** 到达时刻（分片键·计费时间基准 R5） */
    private LocalDateTime arriveTime;
    private String priceVersion;
    private BigDecimal costAmount;
    /** normal/dup/manual */
    private String status;
    private Long originEventId;
}
