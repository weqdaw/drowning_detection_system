package com.hc.drown.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    private String guardId;
    private String realName;
    private String phone;
    private String avatarUrl;
    private Integer status;
    private Integer dutyStatus;
    private LocalDateTime lastLoginAt;
    private Boolean passwordReset;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;

    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String lastWktLocation;

    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String roleCode;

    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private java.util.List<Long> deviceIds;
}
