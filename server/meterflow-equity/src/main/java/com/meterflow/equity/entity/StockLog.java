package com.meterflow.equity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 库存变动明细（I5：已售+锁定≤总库存；与 Redis 预扣对账校平） */
@Data
@TableName("stock_log")
public class StockLog {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long goodsId;
    private String orderNo;
    /** 变动量（可用库存口径：占用为负、释放为正） */
    private Integer changeQty;
    /** lock/release/sale */
    private String reason;
}
