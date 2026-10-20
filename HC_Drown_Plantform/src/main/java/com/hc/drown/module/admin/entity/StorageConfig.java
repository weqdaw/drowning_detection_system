package com.hc.drown.module.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("storage_config")
public class StorageConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long waterAreaId; // null 代表全局默认配置
    private Integer retentionDaysVideo;
    private Integer retentionDaysSnapshot;
    private Boolean backupEnabled;
    private String backupTarget; // NAS, OSS
    private String backupCron;
    private Integer storageWarnThreshold; // 存储预警阈值 ％
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
