package com.hc.drown.module.alert.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hc.drown.common.api.Result;
import com.hc.drown.module.alert.entity.AlertEvent;
import com.hc.drown.module.alert.service.AlertEventService;
import com.hc.drown.module.user.dto.AdminUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "报警管理", description = "AI实时行为报警记录查询及人工确认")
@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
@Validated
public class AlertController {

    private final AlertEventService alertEventService;

    @Operation(summary = "分页获取报警列表 (UC01)")
    @GetMapping
    @PreAuthorize("hasAuthority('alert:read')")
    public Result<Page<AlertEvent>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                         @RequestParam(defaultValue = "10") Integer pageSize,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) Long waterAreaId,
                                         @RequestParam(required = false) String alertType) {
        return Result.success(alertEventService.pageAlertEvents(pageNum, pageSize, status, waterAreaId, alertType));
    }

    @Operation(summary = "获取报警详情 (UC01)")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('alert:read')")
    public Result<AlertEvent> getById(@PathVariable Long id) {
        return Result.success(alertEventService.getAlertEventById(id));
    }

    @Operation(summary = "接收AI主动预警上报 (模拟/触发入口)")
    @PostMapping("/trigger")
    public Result<AlertEvent> trigger(@RequestBody AlertEvent event) {
        return Result.success(alertEventService.receiveAlert(event));
    }

    @Operation(summary = "移动端专享：获取告警事件列表 (支持待处理/已处理状态筛选)")
    @GetMapping("/mobile/list")
    public Result<Page<AlertEvent>> mobileList(@RequestParam(defaultValue = "1") Integer pageNum,
                                               @RequestParam(defaultValue = "20") Integer pageSize,
                                               @RequestParam(required = false) String status) {
        return Result.success(alertEventService.pageAlertEvents(pageNum, pageSize, status, null, null));
    }

    @Operation(summary = "移动端专享：标记告警事件为已处理")
    @PostMapping("/mobile/{id}/process")
    public Result<Boolean> mobileProcessAlert(@PathVariable Long id, @RequestParam(defaultValue = "MobileUser") String handler) {
        AlertEvent event = new AlertEvent();
        event.setId(id);
        event.setStatus("PROCESSED");
        event.setFalseAlarmReason("移动端 App 标记为已处理 (操作员: " + handler + ")");
        event.setUpdatedAt(java.time.LocalDateTime.now());
        return Result.success(alertEventService.updateById(event));
    }

    @Operation(summary = "大屏任务中心：更新任务事件状态 (PENDING / PROCESSED / RECOGNITION_ERROR)")
    @PostMapping("/{id}/status")
    public Result<Boolean> updateAlertStatus(@PathVariable Long id, @RequestParam String status) {
        AlertEvent event = new AlertEvent();
        event.setId(id);
        event.setStatus(status);
        event.setUpdatedAt(java.time.LocalDateTime.now());
        return Result.success(alertEventService.updateById(event));
    }

    @Operation(summary = "值守员人工确认报警 (UC02)")
    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAuthority('alert:confirm')")
    public Result<Boolean> confirm(@PathVariable Long id, @RequestBody ConfirmParam param) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof AdminUserDetails userDetails) {
            Long userId = userDetails.getSysUser().getId();
            return Result.success(alertEventService.confirmAlert(id, param.getIsReal(), param.getReason(), userId));
        }
        return Result.unauthorized(null);
    }

    @Operation(summary = "获取智能目标分析历史记录")
    @GetMapping("/detection-history")
    @PreAuthorize("hasAuthority('alert:read')")
    public Result<List<Map<String, Object>>> getDetectionHistory(@RequestParam(defaultValue = "100") Integer limit) {
        return Result.success(alertEventService.getDetectionHistory(limit));
    }

    @Operation(summary = "逻辑删除报警事件记录")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('alert:close')")
    public Result<Boolean> deleteAlert(@PathVariable Long id) {
        AlertEvent event = new AlertEvent();
        event.setId(id);
        event.setDeleted(1);
        event.setUpdatedAt(java.time.LocalDateTime.now());
        return Result.success(alertEventService.updateById(event));
    }

    @Operation(summary = "删除单条智能目标分析历史记录")
    @DeleteMapping("/detection-history/{id}")
    @PreAuthorize("hasAuthority('alert:close')")
    public Result<Boolean> deleteDetectionHistory(@PathVariable Integer id) {
        return Result.success(alertEventService.deleteDetectionHistory(id));
    }

    @Operation(summary = "清空所有智能目标分析历史记录")
    @DeleteMapping("/detection-history")
    @PreAuthorize("hasAuthority('alert:close')")
    public Result<Boolean> clearDetectionHistory() {
        return Result.success(alertEventService.clearDetectionHistory());
    }

    @Operation(summary = "获取阈值触发抓拍录像列表")
    @GetMapping("/recordings")
    @PreAuthorize("hasAuthority('alert:read')")
    public Result<List<Map<String, Object>>> getRecordings(@RequestParam(required = false) String streamId,
                                                          @RequestParam(defaultValue = "100") Integer limit) {
        return Result.success(alertEventService.getRecordings(streamId, limit));
    }

    @Operation(summary = "删除指定的录像文件及记录")
    @DeleteMapping("/recordings/{id}")
    @PreAuthorize("hasAuthority('alert:close')")
    public Result<Boolean> deleteRecording(@PathVariable Integer id) {
        return Result.success(alertEventService.deleteRecording(id));
    }

    @Operation(summary = "清空所有触发阈值抓拍录像记录及物理文件")
    @DeleteMapping("/recordings")
    @PreAuthorize("hasAuthority('alert:close')")
    public Result<Boolean> clearRecordings() {
        return Result.success(alertEventService.clearRecordings());
    }

    @Data
    public static class ConfirmParam {
        private Boolean isReal;
        private String reason;
    }
}
