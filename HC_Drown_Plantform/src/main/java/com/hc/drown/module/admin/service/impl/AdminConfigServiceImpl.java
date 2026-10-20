package com.hc.drown.module.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hc.drown.module.admin.entity.AuditLog;
import com.hc.drown.module.admin.entity.StorageConfig;
import com.hc.drown.module.admin.mapper.AuditLogMapper;
import com.hc.drown.module.admin.mapper.StorageConfigMapper;
import com.hc.drown.module.admin.service.AdminConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AdminConfigServiceImpl extends ServiceImpl<StorageConfigMapper, StorageConfig> implements AdminConfigService {

    private final AuditLogMapper auditLogMapper;

    @Override
    public Page<AuditLog> pageAuditLogs(Integer pageNum, Integer pageSize, String module, String username, String result) {
        Page<AuditLog> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        if (module != null && !module.isEmpty()) {
            wrapper.eq(AuditLog::getModule, module);
        }
        if (username != null && !username.isEmpty()) {
            wrapper.like(AuditLog::getUsername, username);
        }
        if (result != null && !result.isEmpty()) {
            wrapper.eq(AuditLog::getResult, result);
        }
        wrapper.orderByDesc(AuditLog::getCreatedAt);
        return auditLogMapper.selectPage(page, wrapper);
    }

    @Override
    @Transactional
    public boolean logAudit(AuditLog log) {
        log.setCreatedAt(LocalDateTime.now());
        return auditLogMapper.insert(log) > 0;
    }

    @Override
    public StorageConfig getStorageConfig(Long waterAreaId) {
        // 如果 waterAreaId 为空，查询全局配置 (water_area_id is null)
        LambdaQueryWrapper<StorageConfig> query = new LambdaQueryWrapper<StorageConfig>();
        if (waterAreaId != null) {
            query.eq(StorageConfig::getWaterAreaId, waterAreaId);
        } else {
            query.isNull(StorageConfig::getWaterAreaId);
        }
        StorageConfig config = baseMapper.selectOne(query);
        if (config == null && waterAreaId != null) {
            // 如果该水域没有单独配置，则返回全局默认配置
            config = baseMapper.selectOne(new LambdaQueryWrapper<StorageConfig>().isNull(StorageConfig::getWaterAreaId));
        }
        return config;
    }

    @Override
    @Transactional
    public boolean saveStorageConfig(StorageConfig config, Long userId) {
        config.setUpdatedBy(userId);
        config.setUpdatedAt(LocalDateTime.now());
        if (config.getId() != null) {
            return baseMapper.updateById(config) > 0;
        } else {
            return baseMapper.insert(config) > 0;
        }
    }
}
