package com.hc.drown.module.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hc.drown.module.admin.entity.AuditLog;
import com.hc.drown.module.admin.entity.StorageConfig;

public interface AdminConfigService extends IService<StorageConfig> {

    /**
     * 分页查询审计日志 (UC21)
     */
    Page<AuditLog> pageAuditLogs(Integer pageNum, Integer pageSize, String module, String username, String result);

    /**
     * 写入审计日志记录 (UC21)
     */
    boolean logAudit(AuditLog log);

    /**
     * 获取指定水域或全局默认存储策略 (UC22)
     */
    StorageConfig getStorageConfig(Long waterAreaId);

    /**
     * 修改/制定存储策略 (UC22)
     */
    boolean saveStorageConfig(StorageConfig config, Long userId);
}
