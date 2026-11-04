package com.meterflow.equity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 支付流水（模拟；支付前校验订单状态防超付 R43） */
@Data
@TableName("pay_record")
public class PayRecord {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String orderNo;
    private Long tenantId;
    private Long amountCents;
    private String method;
    private String status;
}
