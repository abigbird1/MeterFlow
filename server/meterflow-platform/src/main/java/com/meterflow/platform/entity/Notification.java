package com.meterflow.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 站内通知（仅站内渠道，默认约定 #8） */
@Data
@TableName("notification")
public class Notification {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String type;
    private String title;
    private String content;
    private Boolean isRead;
}
