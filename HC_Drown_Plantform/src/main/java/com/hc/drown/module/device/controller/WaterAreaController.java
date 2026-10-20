package com.hc.drown.module.device.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hc.drown.common.api.Result;
import com.hc.drown.module.device.entity.WaterArea;
import com.hc.drown.module.device.service.WaterAreaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "水域管理", description = "水域电子围栏及基本参数管理")
@RestController
@RequestMapping("/api/v1/water-areas")
@RequiredArgsConstructor
@Validated
public class WaterAreaController {

    private final WaterAreaService waterAreaService;

    @Operation(summary = "分页查询水域列表")
    @GetMapping
    @PreAuthorize("hasAuthority('waterarea:read')")
    public Result<Page<WaterArea>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                        @RequestParam(defaultValue = "10") Integer pageSize,
                                        @RequestParam(required = false) String keyword) {
        return Result.success(waterAreaService.pageWaterAreas(pageNum, pageSize, keyword));
    }

    @Operation(summary = "根据ID获取水域详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('waterarea:read')")
    public Result<WaterArea> getById(@PathVariable Long id) {
        return Result.success(waterAreaService.getWaterAreaById(id));
    }

    @Operation(summary = "创建新水域")
    @PostMapping
    @PreAuthorize("hasAuthority('waterarea:write')")
    public Result<Boolean> create(@RequestBody WaterArea waterArea) {
        return Result.success(waterAreaService.createWaterArea(waterArea));
    }

    @Operation(summary = "修改水域")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('waterarea:write')")
    public Result<Boolean> update(@PathVariable Long id, @RequestBody WaterArea waterArea) {
        waterArea.setId(id);
        return Result.success(waterAreaService.updateWaterArea(waterArea));
    }

    @Operation(summary = "逻辑删除水域")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('waterarea:write')")
    public Result<Boolean> delete(@PathVariable Long id) {
        WaterArea waterArea = new WaterArea();
        waterArea.setId(id);
        waterArea.setDeleted(1);
        return Result.success(waterAreaService.updateById(waterArea));
    }

    @Operation(summary = "根据经纬度反查所在水域")
    @GetMapping("/containing")
    @PreAuthorize("hasAuthority('waterarea:read')")
    public Result<List<WaterArea>> getContaining(@RequestParam Double lng, @RequestParam Double lat) {
        return Result.success(waterAreaService.findContainingPoint(lng, lat));
    }
}
