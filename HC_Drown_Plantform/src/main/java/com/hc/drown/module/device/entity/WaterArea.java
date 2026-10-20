package com.hc.drown.module.device.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("water_area")
public class WaterArea {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String areaCode;
    private Integer level; // 1: 重点, 2: 普通

    // 空间字段使用 String 承载 WKT (Well-Known Text) 格式
    private String boundary;
    private String centerPoint;

    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;

    // 辅助展示字段（经纬度），不映射数据库表
    @TableField(exist = false)
    private Double centerLng;
    @TableField(exist = false)
    private Double centerLat;
}
