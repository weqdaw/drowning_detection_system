package com.hc.drown.module.statistics.controller;

import com.hc.drown.common.api.Result;
import com.hc.drown.module.statistics.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "统计报表管理", description = "数据可视化大屏及历史趋势统计分析")
@RestController
@RequestMapping("/api/v1/statistics")
@RequiredArgsConstructor
@Validated
public class StatisticsController {

    private final StatisticsService statisticsService;

    @Operation(summary = "全域安全态势指标总览 (今日实时大屏) (UC13)")
    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('stats:read')")
    public Result<Map<String, Object>> getOverview() {
        return Result.success(statisticsService.getOverviewStats());
    }

    @Operation(summary = "历史报警/处置趋势分析 (UC15)")
    @GetMapping("/trends")
    @PreAuthorize("hasAuthority('stats:read')")
    public Result<Map<String, Object>> getTrends(@RequestParam(defaultValue = "7") Integer days) {
        return Result.success(statisticsService.getAlertTrends(days));
    }

    @Operation(summary = "按水域统计报警频度分布")
    @GetMapping("/water-areas")
    @PreAuthorize("hasAuthority('stats:read')")
    public Result<Map<String, Object>> getWaterAreasDistribution() {
        return Result.success(statisticsService.getWaterAreaStats());
    }
}
