package com.hc.drown.module.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("lifeguard_location")
public class LifeguardLocation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String location; // PostGIS point
    private BigDecimal accuracy; // GPS精度
    private Integer dutyStatus; // 在岗状态 1:在岗, 0:离岗
    private LocalDateTime recordedAt;
}
