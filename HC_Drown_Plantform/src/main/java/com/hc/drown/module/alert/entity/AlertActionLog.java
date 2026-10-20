package com.hc.drown.module.alert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("alert_action_log")
public class AlertActionLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long eventId;
    private String actionType; // UAV_DISPATCH, SPEAKER_BROADCAST, CAMERA_PRESET, TASK_CREATE
    private Long targetId; // 关联设备ID或任务ID
    private String status; // SUCCESS, FAILED
    private String detail;
    private LocalDateTime executedAt;
}
