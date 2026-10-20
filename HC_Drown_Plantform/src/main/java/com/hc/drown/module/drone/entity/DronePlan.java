package com.hc.drown.module.drone.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("drone_plan")
public class DronePlan {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String planName;
    private Long hangarId; // 关联的机库设备ID
    private Long waterAreaId;
    private String scheduleType; // ONCE, DAILY, WEEKLY
    private String cronExpression;
    private LocalDateTime nextExecAt;
    private Integer flightAltitude; // 飞行高度（米）
    private BigDecimal flightSpeed; // 速度
    private String status; // ACTIVE, PAUSED, DELETED
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;

    // 辅助字段
    @TableField(exist = false)
    private String hangarName;
    @TableField(exist = false)
    private String waterAreaName;
    @TableField(exist = false)
    private List<DroneWaypoint> waypoints;
}
