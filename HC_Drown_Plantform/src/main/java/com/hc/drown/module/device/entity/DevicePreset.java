package com.hc.drown.module.device.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("device_preset")
public class DevicePreset {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long deviceId;
    private Integer presetNo;
    private String presetName;
    private String description;
}
