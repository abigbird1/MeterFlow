package com.meterflow.billing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 停服/恢复记录（R22/R23；detail 内含 topProduct 供产品级拒收判定） */
@Data
@TableName("suspension_log")
public class SuspensionLog {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    /** warn/suspend/restore */
    private String action;
    private String detail;
    private LocalDateTime createdAt;
}
