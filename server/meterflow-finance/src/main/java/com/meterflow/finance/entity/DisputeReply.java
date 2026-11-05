package com.meterflow.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 工单处理回复 */
@Data
@TableName("dispute_reply")
public class DisputeReply {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long disputeId;
    private String replier;
    private String content;
    private LocalDateTime createdAt;
}
