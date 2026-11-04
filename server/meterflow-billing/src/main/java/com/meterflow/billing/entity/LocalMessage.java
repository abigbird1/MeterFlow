package com.meterflow.billing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 本地消息表（R-8 最终一致：出站事件，消费方幂等） */
@Data
@TableName("local_message")
public class LocalMessage {
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
