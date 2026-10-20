package com.hc.drown.module.drone.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hc.drown.common.api.Result;
import com.hc.drown.module.drone.entity.DroneFlightLog;
import com.hc.drown.module.drone.entity.DroneMission;
import com.hc.drown.module.drone.entity.DronePlan;
import com.hc.drown.module.drone.service.DronePatrolService;
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

@Tag(name = "无人机巡检管理", description = "制定巡航轨迹计划、任务追踪、遥测轨迹数据查询接口")
@RestController
@RequestMapping("/api/v1/drones")
@RequiredArgsConstructor
@Validated
public class DronePatrolController {

    private final DronePatrolService dronePatrolService;

    @Operation(summary = "制定巡航计划 (UC08)")
    @PostMapping("/plans")
    @PreAuthorize("hasAuthority('drone:plan')")
    public Result<Boolean> createPlan(@RequestBody DronePlan plan) {
        return Result.success(dronePatrolService.createPlan(plan));
    }

    @Operation(summary = "查看巡航计划详情")
    @GetMapping("/plans/{id}")
    @PreAuthorize("hasAuthority('drone:read')")
    public Result<DronePlan> getPlan(@PathVariable Long id) {
        return Result.success(dronePatrolService.getPlanById(id));
    }

    @Operation(summary = "调度无人机起飞 (UC04)")
    @PostMapping("/takeoff")
    @PreAuthorize("hasAuthority('drone:control')")
    public Result<DroneMission> takeoff(@RequestParam(required = false) Long alertEventId,
                                        @RequestParam Long hangarId) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof AdminUserDetails userDetails) {
            Long operatorId = userDetails.getSysUser().getId();
            return Result.success(dronePatrolService.triggerEmergencyTakeoff(alertEventId, hangarId, operatorId));
        }
        return Result.unauthorized(null);
    }

    @Operation(summary = "分页获取无人机任务记录")
    @GetMapping("/missions")
    @PreAuthorize("hasAuthority('drone:read')")
    public Result<Page<DroneMission>> getMissions(@RequestParam(defaultValue = "1") Integer pageNum,
                                                  @RequestParam(defaultValue = "10") Integer pageSize,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(required = false) Long droneId) {
        return Result.success(dronePatrolService.pageMissions(pageNum, pageSize, status, droneId));
    }

    @Operation(summary = "指令无人机返航 (UC04)")
    @PostMapping("/missions/{id}/end")
    @PreAuthorize("hasAuthority('drone:control')")
    public Result<Boolean> endMission(@PathVariable Long id) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof AdminUserDetails userDetails) {
            Long operatorId = userDetails.getSysUser().getId();
            return Result.success(dronePatrolService.endMission(id, operatorId));
        }
        return Result.unauthorized(null);
    }

    @Operation(summary = "查看遥测轨迹历史 (F-17)")
    @GetMapping("/missions/{id}/telemetry")
    @PreAuthorize("hasAuthority('drone:read')")
    public Result<List<DroneFlightLog>> getTelemetry(@PathVariable Long id) {
        return Result.success(dronePatrolService.getTelemetryHistory(id));
    }

    @Operation(summary = "上报实时遥测信息 (无人机端点)")
    @PostMapping("/telemetry")
    public Result<Boolean> uploadTelemetry(@RequestBody TelemetryParam param) {
        DroneFlightLog log = new DroneFlightLog();
        log.setMissionId(param.getMissionId());
        log.setAltitude(param.getAltitude());
        log.setSpeed(param.getSpeed());
        log.setBatteryPct(param.getBatteryPct());
        log.setSignalDbm(param.getSignalDbm());

        return Result.success(dronePatrolService.uploadTelemetry(log, param.getLongitude(), param.getLatitude()));
    }

    @Data
    public static class TelemetryParam {
        private Long missionId;
        private Double longitude;
        private Double latitude;
        private BigDecimal altitude;
        private BigDecimal speed;
        private Integer batteryPct;
        private Integer signalDbm;
    }
}
