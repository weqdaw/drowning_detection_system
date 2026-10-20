package com.hc.drown.module.device.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@TableName(value = "device", autoResultMap = true)
public class Device {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String deviceCode;
    private String deviceName;
    private String deviceType; // CAMERA, SPEAKER, DRONE_HANGAR, DRONE
    private String manufacturer;
    private String model;
    private String ipAddress;
    private Integer port;

    // 空间字段使用 WKTPoint
    private String location;
    private String locationDesc;
    private Long waterAreaId;
    private String status; // ONLINE, OFFLINE, FAULT, MAINTENANCE
    private LocalDateTime lastHeartbeat;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extraConfig; // 设备特有配置

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;

    // 辅助字段
    @TableField(exist = false)
    private Double longitude;
    @TableField(exist = false)
    private Double latitude;
    @TableField(exist = false)
    private String waterAreaName;
}
