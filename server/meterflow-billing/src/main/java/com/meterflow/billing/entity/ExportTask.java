package com.meterflow.billing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 异步导出任务（AF2：账单/明细 CSV；文件保留 7 天） */
@Data
@TableName("export_task")
public class ExportTask {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    /** bill/events */
    private String taskType;
    /** 导出参数 JSON */
    private String params;
    /** processing/done/failed */
    private String status;
    private String fileKey;
    private Long fileRows;
    private String failReason;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
}
