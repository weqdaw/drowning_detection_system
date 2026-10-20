package com.hc.drown.module.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "rescue_task", autoResultMap = true)
public class RescueTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskNo;
    private Long eventId;
    private Long assigneeId; // 救生员ID
    private Long dispatcherId; // 调度人ID (若为自动分配，则为null或系统预留ID)
    private String status; // PENDING, DISPATCHED, ACCEPTED, ARRIVED, PROCESSING, COMPLETED, PROCESSED, RECOGNITION_ERROR, FALSE_ALARM, CANCELLED

    // PostGIS point
    private String taskLocation;

    private Integer priority; // 1:普通 2:紧急 3:特级
    private LocalDateTime dispatchAt;
    private LocalDateTime acceptAt;
    private LocalDateTime arriveAt;
    private LocalDateTime completeAt;
    private String resultType; // RESCUED, PERSUADED, FALSE_ALARM, NEED_SUPPORT
    private String resultNote;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> resultPhotoUrls; // 现场照片

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;

    // 辅助字段
    @TableField(exist = false)
    private Double longitude;
    @TableField(exist = false)
    private Double latitude;
    @TableField(exist = false)
    private String assigneeName;
    @TableField(exist = false)
    private String assigneePhone;
    @TableField(exist = false)
    private String dispatcherName;
    @TableField(exist = false)
    private String eventNo;
    @TableField(exist = false)
    private String snapshotUrl;
    @TableField(exist = false)
    private String sourceDeviceName;
    @TableField(exist = false)
    private String sourceDeviceCode;
    @TableField(exist = false)
    private Long sourceDeviceId;
    @TableField(exist = false)
    private BigDecimal confidence;
    @TableField(exist = false)
    private String alertType;
    @TableField(exist = false)
    private String warningMsg;
}
