package com.meterflow.billing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 资源包实例（S4 余量归 billing；不退订到期清零 R33） */
@Data
@TableName("package_instance")
public class PackageInstance {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    /** spec/common */
    private String pkgType;
    private String title;
    private Long productId;
    private String metricKey;
    private BigDecimal total;
    private BigDecimal remaining;
    /** Token/次/元 */
    private String unit;
    private LocalDate expireDate;
    /** active/exhausted/expired */
    private String status;
    private String sourceOrderNo;
}
