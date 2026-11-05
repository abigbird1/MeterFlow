package com.meterflow.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 幂等任务实例（R42：uk(task_name, biz_key)） */
@Data
@TableName("task_instance")
public class TaskInstance {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String taskName;
    private String bizKey;
    /** init/running/success/failed */
    private String status;
    private Integer retryCount;
    private LocalDateTime lastRunAt;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
