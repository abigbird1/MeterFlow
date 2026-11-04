package com.meterflow.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 角色权限点（权限点=功能域:操作，如 bills:detail） */
@Data
@TableName("role_permission")
public class RolePermission {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long roleId;
    private String permCode;
}
