package com.hc.drown.module.drone.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("drone_waypoint")
public class DroneWaypoint {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long planId;
    private Integer seqNo;

    // PostGIS point
    private String location;

    private Integer altitude;
    private Integer hoverSec;
    private String action; // HOVER, PHOTO, VIDEO

    // 辅助字段
    @TableField(exist = false)
    private Double longitude;
    @TableField(exist = false)
    private Double latitude;
}
