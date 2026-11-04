package com.meterflow.equity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 抢购订单（R32：下单锁库存；R43：15 分钟超时释放，支付以状态为准） */
@Data
@TableName("goods_order")
public class GoodsOrder {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String orderNo;
    private Long tenantId;
    private Long goodsId;
    private Integer qty;
    private Long amountCents;
    /** pending/paid/closed/canceled */
    private String status;
    private LocalDateTime payExpireAt;
    private LocalDateTime paidAt;
    private String closeNote;
    private LocalDateTime createdAt;
}
