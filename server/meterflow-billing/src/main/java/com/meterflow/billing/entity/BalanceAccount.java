package com.meterflow.billing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 余额账户（租户 1:1；行级锁串行 P7；资金写路径唯一入口 P3） */
@Data
@TableName("balance_account")
public class BalanceAccount {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long balanceCents;
    private Long overdraftCents;
    /** normal/warning/suspended（分级停服 R22） */
    private String status;
    private Long version;
}
