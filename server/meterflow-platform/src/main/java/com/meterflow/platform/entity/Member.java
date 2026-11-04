package com.meterflow.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 租户成员（邀请直接生效 B11；部门级数据隔离边界见 6.1） */
@Data
@TableName("member")
public class Member {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String name;
    private String email;
    private String passwordHash;
    private Long deptId;
    private Long roleId;
    /** active/disabled */
    private String status;
}
