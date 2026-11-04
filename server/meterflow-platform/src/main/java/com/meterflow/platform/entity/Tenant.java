package com.meterflow.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 租户（R25 状态机；白名单透支阈值 R24） */
@Data
@TableName("tenant")
public class Tenant {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String name;
    private String email;
    private String passwordHash;
    /** normal/warning/suspended/disabled */
    private String status;
    /** 白名单透支阈值（分）；NULL=适用平台统一阈值 */
    private Long overdraftCents;
    private Boolean isWhitelist;
}
