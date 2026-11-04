package com.meterflow.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 角色（preset 预设模板 tenant_id=NULL；custom 自定义） */
@Data
@TableName("role")
public class Role {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String name;
    /** preset/custom */
    private String roleType;
    private String status;
}
