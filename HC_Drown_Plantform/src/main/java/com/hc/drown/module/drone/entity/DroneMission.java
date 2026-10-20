package com.hc.drown.module.drone.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("drone_mission")
public class DroneMission {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String missionNo;
    private Long planId;
    private Long droneId;
    private String triggerType; // SCHEDULED, MANUAL, EMERGENCY
    private Long triggerEventId; // 关联的报警事件ID
    private String status; // PENDING, FLYING, RETURNING, COMPLETED, ABORTED
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private BigDecimal flightDistance;
    private String reportUrl;
    private Long operatorId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;

    // 辅助字段
    @TableField(exist = false)
    private String droneName;
    @TableField(exist = false)
    private String planName;
    @TableField(exist = false)
    private String operatorName;
}
