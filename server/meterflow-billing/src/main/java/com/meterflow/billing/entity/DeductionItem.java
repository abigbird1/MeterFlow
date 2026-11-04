package com.meterflow.billing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 抵扣来源明细（D2：对账与解释粒度） */
@Data
@TableName("deduction_item")
public class DeductionItem {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long recordId;
    private Long tenantId;
    /** gift/builtin/pkg_spec/pkg_common/balance */
    private String sourceType;
    private Long sourceId;
    private String sourceName;
    private BigDecimal amount;
    private LocalDateTime createdAt;
}
