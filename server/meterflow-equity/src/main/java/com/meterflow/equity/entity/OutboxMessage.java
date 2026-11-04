package com.meterflow.equity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 本地消息表（S4：订单已支付 → relay 投递 billing 发放实例；MQ 接入后仅换投递通道） */
@Data
@TableName("outbox_message")
public class OutboxMessage {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String topic;
    private String bizKey;
    private String payload;
    /** init/sent/confirmed */
    private String status;
    private Integer retryCount;
    private LocalDateTime nextRetryAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
