package com.hc.drown.module.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hc.drown.common.api.Result;
import com.hc.drown.module.admin.entity.AuditLog;
import com.hc.drown.module.admin.entity.StorageConfig;
import com.hc.drown.module.admin.service.AdminConfigService;
import com.hc.drown.module.user.dto.AdminUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "系统后台配置管理", description = "日志审计与文件存储清理策略配置")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Validated
public class AdminConfigController {

    private final AdminConfigService adminConfigService;

    @Operation(summary = "查询日志审计记录 (UC21)")
    @GetMapping("/audit-logs")
    @PreAuthorize("hasAuthority('audit:read')")
    public Result<Page<AuditLog>> getAuditLogs(@RequestParam(defaultValue = "1") Integer pageNum,
                                               @RequestParam(defaultValue = "10") Integer pageSize,
                                               @RequestParam(required = false) String module,
                                               @RequestParam(required = false) String username,
                                               @RequestParam(required = false) String result) {
        return Result.success(adminConfigService.pageAuditLogs(pageNum, pageSize, module, username, result));
    }

    @Operation(summary = "写入操作日志审计 (UC21)")
    @PostMapping("/audit-logs")
    public Result<Boolean> writeAuditLog(@RequestBody AuditLog log) {
        return Result.success(adminConfigService.logAudit(log));
    }

    @Operation(summary = "获取存储配置策略 (UC22)")
    @GetMapping("/storage-configs")
    @PreAuthorize("hasAuthority('storage:config')")
    public Result<StorageConfig> getStorageConfig(@RequestParam(required = false) Long waterAreaId) {
        return Result.success(adminConfigService.getStorageConfig(waterAreaId));
    }

    @Operation(summary = "设置存储清理策略 (UC22)")
    @PostMapping("/storage-configs")
    @PreAuthorize("hasAuthority('storage:config')")
    public Result<Boolean> saveStorageConfig(@RequestBody StorageConfig config) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof AdminUserDetails userDetails) {
            Long userId = userDetails.getSysUser().getId();
            return Result.success(adminConfigService.saveStorageConfig(config, userId));
        }
        return Result.unauthorized(null);
    }
}
