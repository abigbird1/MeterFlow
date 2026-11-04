package com.meterflow.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/** API Key（租户级多 Key；hash 存储 ET4；轮换并行 24h） */
@Data
@TableName("api_key")
public class ApiKey {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String name;
    private String note;
    /** SHA-256 散列（不序列化出参，ET4；明文仅创建/轮换时一次性返回） */
    @JsonIgnore
    private String keyHash;
    private String keyMask;
    /** 绑定部门（事件部门归属依据）；NULL=未分配 */
    private Long deptId;
    /** 限定产品集 JSON；NULL=全部产品 */
    private String products;
    /** enabled/disabled/rotating */
    private String status;
    /** 轮换并行截止（旧 Key 失效时刻） */
    private LocalDateTime rotateDeadline;
    private LocalDateTime lastUsedAt;

    /** 明文 Key（仅创建/轮换响应携带，不落库不缓存） */
    @TableField(exist = false)
    private String plainKey;
}
