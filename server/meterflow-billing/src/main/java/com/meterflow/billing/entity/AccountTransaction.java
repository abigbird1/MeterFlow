package com.meterflow.billing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 余额变动流水（P3 三入口：充值/抵扣/调整；R26 计费vs账户对账依据；分表逻辑表） */
@Data
@TableName("account_transaction")
public class AccountTransaction {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    /** recharge/deduct/adjust */
    private String txType;
    private String refType;
    private Long refId;
    /** 变动额（分，正负号） */
    private Long amountCents;
    private Long balanceAfterCents;
    private LocalDateTime createdAt;
}
