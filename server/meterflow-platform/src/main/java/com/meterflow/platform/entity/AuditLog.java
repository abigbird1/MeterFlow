package com.meterflow.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 审计日志（分表逻辑表 audit_log_yyyyMM 的单表骨架版；敏感操作全留痕 5.10） */
@Data
@TableName("audit_log")
public class AuditLog {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long actorId;
    private String actorName;
    private String actorRole;
    private String action;
    private String target;
    private String scope;
    private String result;
    private Boolean isSensitive;
    private java.time.LocalDateTime createdAt;
}
