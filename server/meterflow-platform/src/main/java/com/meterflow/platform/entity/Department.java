package com.meterflow.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 部门（多级树；部门级数据隔离边界） */
@Data
@TableName("department")
public class Department {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    /** 上级部门；NULL=根（租户本体） */
    private Long parentId;
    private String name;
}
