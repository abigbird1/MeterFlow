package com.meterflow.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 账单部门小计（R19；骨架按汇总口径） */
@Data
@TableName("bill_dept_sub")
public class BillDeptSub {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long billId;
    private String deptName;
    private BigDecimal amountCents;
    private LocalDateTime createdAt;
}
