package com.hc.drown.module.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rescue_task_log")
public class RescueTaskLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long operatorId;
    private String action; // DISPATCH, ACCEPT, ARRIVE, COMPLETE, CANCEL
    private String note;
    private String location; // PostGIS point
    private LocalDateTime loggedAt;
}
