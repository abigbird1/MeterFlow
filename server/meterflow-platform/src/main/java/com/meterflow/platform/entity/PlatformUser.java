package com.meterflow.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 平台账号（六岗位：sadmin/ops/cs/reviewer/fin/audit，需求 6.2） */
@Data
@TableName("platform_user")
public class PlatformUser {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String name;
    private String email;
    private String passwordHash;
    private String role;
    private String status;
}
