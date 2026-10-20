package com.hc.drown.module.drone.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("drone_flight_log")
public class DroneFlightLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long missionId;
    private String location; // PostGIS point
    private BigDecimal altitude;
    private BigDecimal speed;
    private Integer batteryPct;
    private Integer signalDbm;
    private LocalDateTime loggedAt;
}
