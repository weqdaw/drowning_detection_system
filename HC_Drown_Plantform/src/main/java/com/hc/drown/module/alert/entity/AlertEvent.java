package com.hc.drown.module.alert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("alert_event")
public class AlertEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventNo;
    private Long sourceDeviceId;
    private Long waterAreaId;

    // PostGIS Point
    private String alertLocation;

    private BigDecimal confidence;
    private String alertType; // DROWNING, ABNORMAL
    private String status; // PENDING, CONFIRMED, FALSE_ALARM, RESOLVED
    private String snapshotUrl;
    private String videoClipUrl;
    private Long confirmedBy;
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime confirmedAt;
    private String falseAlarmReason;
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime resolvedAt;
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedAt;
    private Integer deleted;

    // 辅助展示字段
    @TableField(exist = false)
    private Double longitude;
    @TableField(exist = false)
    private Double latitude;
    @TableField(exist = false)
    private String sourceDeviceName;
    @TableField(exist = false)
    private String waterAreaName;
    @TableField(exist = false)
    private String confirmedByName;
}
