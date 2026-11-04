package com.meterflow.billing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 充值记录（模拟支付·即时到账 R16；单笔限额 R40） */
@Data
@TableName("recharge_record")
public class RechargeRecord {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long amountCents;
    private String method;
    private String status;
    private String payNo;
}
