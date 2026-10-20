package com.hc.drown.module.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audit_log")
public class AuditLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String username;
    private String module; // ALERT, DEVICE, USER, TASK, DRONE, ADMIN
    private String action;
    private String targetId;
    private String detail;
    private String ipAddress;
    private String result; // SUCCESS, FAILED
    private LocalDateTime createdAt;
}
