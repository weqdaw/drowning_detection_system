package com.hc.drown.module.statistics.service;

import java.util.Map;

public interface StatisticsService {

    /**
     * 获取全域安全态势指标 (今日统计) (UC13)
     */
    Map<String, Object> getOverviewStats();

    /**
     * 获取报警和处置的趋势统计 (按月或按周) (UC15)
     */
    Map<String, Object> getAlertTrends(Integer days);

    /**
     * 获取按水域划分的报警高发热度 (UC15)
     */
    Map<String, Object> getWaterAreaStats();
}
