package com.meterflow.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 每日对账结果（R26；uk(recon_date) 重跑幂等 R42） */
@Data
@TableName("daily_recon")
public class DailyRecon {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private LocalDate reconDate;
    private Long eventCount;
    private Integer diffAb;
    private Integer diffBc;
    /** ok/err */
    private String result;
    private LocalDateTime createdAt;
}
