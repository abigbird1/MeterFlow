package com.meterflow.billing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 价格版本（R11：即时生效，账期内分段） */
@Data
@TableName("price_version")
public class PriceVersion {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long productId;
    private Integer versionNo;
    private LocalDateTime effectiveAt;
    private String note;
    private String createdBy;
}
