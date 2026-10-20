package com.hc.drown.module.device.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hc.drown.common.api.Result;
import com.hc.drown.module.device.entity.Device;
import com.hc.drown.module.device.service.DeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "设备管理", description = "摄像头、音柱、无人机机库等设备管理及控制")
@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
@Validated
public class DeviceController {

    private final DeviceService deviceService;

    @Operation(summary = "分页获取设备列表 (UC18)")
    @GetMapping
    @PreAuthorize("hasAuthority('device:read')")
    public Result<Page<Device>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                     @RequestParam(defaultValue = "10") Integer pageSize,
                                     @RequestParam(required = false) String keyword,
                                     @RequestParam(required = false) String type,
                                     @RequestParam(required = false) Long waterAreaId) {
        return Result.success(deviceService.pageDevices(pageNum, pageSize, keyword, type, waterAreaId));
    }

    @Operation(summary = "获取设备详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('device:read')")
    public Result<Device> getById(@PathVariable Long id) {
        return Result.success(deviceService.getDeviceById(id));
    }

    @Operation(summary = "设备注册 (UC18)")
    @PostMapping
    @PreAuthorize("hasAuthority('device:write')")
    public Result<Boolean> create(@RequestBody Device device) {
        return Result.success(deviceService.createDevice(device));
    }

    @Operation(summary = "修改设备 (UC18)")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('device:write')")
    public Result<Boolean> update(@PathVariable Long id, @RequestBody Device device) {
        device.setId(id);
        return Result.success(deviceService.updateDevice(device));
    }

    @Operation(summary = "删除设备 (UC18)")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('device:delete')")
    public Result<Boolean> delete(@PathVariable Long id) {
        Device device = new Device();
        device.setId(id);
        device.setDeleted(1);
        return Result.success(deviceService.updateById(device));
    }

    @Operation(summary = "设备上报心跳/状态")
    @PostMapping("/heartbeat")
    public Result<Boolean> heartbeat(@RequestParam String deviceCode, @RequestParam String status) {
        return Result.success(deviceService.updateHeartbeat(deviceCode, status));
    }

    @Operation(summary = "查询最近的特定类型设备 (联动推荐最近音柱等)")
    @GetMapping("/nearest")
    @PreAuthorize("hasAuthority('device:read')")
    public Result<Device> getNearest(@RequestParam Double lng,
                                     @RequestParam Double lat,
                                     @RequestParam String type) {
        return Result.success(deviceService.findNearestDevice(lng, lat, type));
    }

    @Operation(summary = "针对摄像头设备动态切换AI算法模型 (热重载)")
    @PostMapping("/{id}/model")
    @PreAuthorize("hasAuthority('device:control')")
    public Result<Boolean> switchModel(@PathVariable Long id, @RequestBody ModelParam param) {
        return Result.success(deviceService.switchModel(id, param.getModelName()));
    }

    @Operation(summary = "针对摄像头设备动态微调识别精度/灵敏度阈值 (滑轨控制/热重载)")
    @PostMapping("/{id}/confidence")
    @PreAuthorize("hasAuthority('device:control')")
    public Result<Boolean> updateConfidence(@PathVariable Long id, @RequestBody ConfidenceParam param) {
        return Result.success(deviceService.updateConfidence(id, param.getConfidence()));
    }

    @lombok.Data
    public static class ModelParam {
        private String modelName;
    }

    @lombok.Data
    public static class ConfidenceParam {
        private Float confidence;
    }
}
