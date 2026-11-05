package com.meterflow.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 账单分段（产品×价格版本 R19） */
@Data
@TableName("bill_section")
public class BillSection {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long billId;
    private Long productId;
    private String version;
    private LocalDate rangeStart;
    private LocalDate rangeEnd;
    private BigDecimal amountCents;
    private LocalDateTime createdAt;
}
