package com.hc.drown.module.task.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hc.drown.common.api.Result;
import com.hc.drown.module.task.entity.LifeguardLocation;
import com.hc.drown.module.task.entity.RescueTask;
import com.hc.drown.module.task.service.RescueTaskService;
import com.hc.drown.module.user.dto.AdminUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Tag(name = "救援任务管理", description = "救生员救援任务派发、流转状态控制及位置上报")
@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
@Validated
public class RescueTaskController {

    private final RescueTaskService rescueTaskService;
    private final com.hc.drown.module.user.service.SysUserService sysUserService;

    @Operation(summary = "分页获取任务列表 (UC11)")
    @GetMapping
    @PreAuthorize("hasAuthority('task:read')")
    public Result<Page<RescueTask>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                         @RequestParam(defaultValue = "10") Integer pageSize,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) Long assigneeId,
                                         @RequestParam(required = false) Long eventId) {
        return Result.success(rescueTaskService.pageRescueTasks(pageNum, pageSize, status, assigneeId, eventId));
    }

    @Operation(summary = "获取救援任务详情 (UC09)")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('task:read')")
    public Result<RescueTask> getById(@PathVariable Long id) {
        return Result.success(rescueTaskService.getRescueTaskById(id));
    }

    @Operation(summary = "手动指派/调度人员 (UC02 / UC09)")
    @PostMapping("/dispatch")
    @PreAuthorize("hasAuthority('task:dispatch')")
    public Result<RescueTask> dispatch(@RequestParam Long eventId) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof AdminUserDetails userDetails) {
            Long dispatcherId = userDetails.getSysUser().getId();
            return Result.success(rescueTaskService.dispatchTask(eventId, dispatcherId));
        }
        return Result.unauthorized(null);
    }

    @Operation(summary = "接收/接受任务 (UC09)")
    @PostMapping("/{id}/accept")
    @PreAuthorize("hasAuthority('task:update')")
    public Result<Boolean> accept(@PathVariable Long id) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof AdminUserDetails userDetails) {
            Long userId = userDetails.getSysUser().getId();
            return Result.success(rescueTaskService.acceptTask(id, userId));
        }
        return Result.unauthorized(null);
    }

    @Operation(summary = "到达现场上报 (UC10)")
    @PostMapping("/{id}/arrive")
    @PreAuthorize("hasAuthority('task:update')")
    public Result<Boolean> arrive(@PathVariable Long id, @RequestBody ArriveParam param) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof AdminUserDetails userDetails) {
            Long userId = userDetails.getSysUser().getId();
            return Result.success(rescueTaskService.arriveTask(id, userId, param.getLongitude(), param.getLatitude()));
        }
        return Result.unauthorized(null);
    }

    @Operation(summary = "完成救援任务 (UC10)")
    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('task:update')")
    public Result<Boolean> complete(@PathVariable Long id, @RequestBody CompleteParam param) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof AdminUserDetails userDetails) {
            Long userId = userDetails.getSysUser().getId();
            return Result.success(rescueTaskService.completeTask(id, userId, param.getResultType(), param.getNote(), param.getPhotoUrls()));
        }
        return Result.unauthorized(null);
    }

    @Operation(summary = "标注更新任务状态 (已处理 / 识别错误)")
    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('task:update')")
    public Result<Boolean> updateStatus(@PathVariable Long id,
                                        @RequestParam String status,
                                        @RequestParam(required = false) String note) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long userId = 1L;
        if (principal instanceof AdminUserDetails userDetails) {
            userId = userDetails.getSysUser().getId();
        }
        return Result.success(rescueTaskService.updateTaskStatus(id, status, userId, note));
    }

    @Operation(summary = "上报实时定位 (UC12 / F-19)")
    @PostMapping("/location")
    @PreAuthorize("hasAuthority('task:update')")
    public Result<Boolean> uploadLocation(@RequestBody LocationParam param) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof AdminUserDetails userDetails) {
            Long userId = userDetails.getSysUser().getId();
            com.hc.drown.module.user.entity.SysUser user = sysUserService.getById(userId);
            Integer dutyStatus = (user != null && user.getDutyStatus() != null) ? user.getDutyStatus() : userDetails.getSysUser().getDutyStatus();

            LifeguardLocation loc = new LifeguardLocation();
            loc.setUserId(userId);
            loc.setAccuracy(param.getAccuracy() != null ? param.getAccuracy() : new BigDecimal("5.0"));
            loc.setDutyStatus(dutyStatus);

            return Result.success(rescueTaskService.uploadLocation(loc, param.getLongitude(), param.getLatitude()));
        }
        return Result.unauthorized(null);
    }

    @Data
    public static class ArriveParam {
        private Double longitude;
        private Double latitude;
    }

    @Data
    public static class CompleteParam {
        private String resultType; // RESCUED, PERSUADED, FALSE_ALARM, NEED_SUPPORT
        private String note;
        private List<String> photoUrls;
    }

    @Data
    public static class LocationParam {
        private Double longitude;
        private Double latitude;
        private BigDecimal accuracy;
    }
}
