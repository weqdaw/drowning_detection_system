package com.hc.drown.module.statistics.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hc.drown.module.alert.entity.AlertEvent;
import com.hc.drown.module.alert.mapper.AlertEventMapper;
import com.hc.drown.module.device.entity.Device;
import com.hc.drown.module.device.mapper.DeviceMapper;
import com.hc.drown.module.statistics.service.StatisticsService;
import com.hc.drown.module.task.entity.RescueTask;
import com.hc.drown.module.task.mapper.RescueTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final AlertEventMapper alertEventMapper;
    private final DeviceMapper deviceMapper;
    private final RescueTaskMapper rescueTaskMapper;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Override
    public Map<String, Object> getOverviewStats() {
        Map<String, Object> stats = new HashMap<>();

        // 今日时间区间
        LocalDateTime startOfToday = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime endOfToday = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);

        // 1. 今日报警数
        Long todayAlertCount = alertEventMapper.selectCount(new LambdaQueryWrapper<AlertEvent>()
                .ge(AlertEvent::getCreatedAt, startOfToday)
                .le(AlertEvent::getCreatedAt, endOfToday)
                .eq(AlertEvent::getDeleted, 0));
        stats.put("todayAlertCount", todayAlertCount);

        // 2. 未处理报警数
        Long pendingAlertCount = alertEventMapper.selectCount(new LambdaQueryWrapper<AlertEvent>()
                .eq(AlertEvent::getStatus, "PENDING")
                .eq(AlertEvent::getDeleted, 0));
        stats.put("pendingAlertCount", pendingAlertCount);

        // 3. 设备总数 & 在线数
        Long totalDevice = deviceMapper.selectCount(new LambdaQueryWrapper<Device>()
                .eq(Device::getDeleted, 0));
        Long onlineDevice = deviceMapper.selectCount(new LambdaQueryWrapper<Device>()
                .eq(Device::getStatus, "ONLINE")
                .eq(Device::getDeleted, 0));
        stats.put("totalDeviceCount", totalDevice);
        stats.put("onlineDeviceCount", onlineDevice);
        stats.put("deviceOnlineRate", totalDevice > 0 ? (double) onlineDevice / totalDevice : 0.0);

        // 4. 今日已完成救援任务
        Long completedRescueCount = rescueTaskMapper.selectCount(new LambdaQueryWrapper<RescueTask>()
                .eq(RescueTask::getStatus, "COMPLETED")
                .ge(RescueTask::getCompleteAt, startOfToday)
                .le(RescueTask::getCompleteAt, endOfToday)
                .eq(RescueTask::getDeleted, 0));
        stats.put("todayCompletedRescueCount", completedRescueCount);

        // 5. 真实涉水人数：从设备表中各个摄像头的 extra_config 字段提取 counts 中的 Person 和 Drowning 数量进行汇总
        Integer peopleCount = 0;
        try {
            peopleCount = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(COALESCE((extra_config->'counts'->>'Person')::integer, 0) + COALESCE((extra_config->'counts'->>'Drowning')::integer, 0)), 0) FROM device WHERE device_type = 'CAMERA' AND deleted = 0",
                Integer.class
            );
        } catch (Exception e) {
            // 容错降级
        }
        stats.put("peopleCount", peopleCount != null ? peopleCount : 0);

        // 6. 在岗救生员力量：统计未删除且值班状态为在岗值班(1)或救援中(2)的用户数量
        Integer lifeguardCount = 0;
        try {
            lifeguardCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE duty_status IN (1, 2) AND deleted = 0",
                Integer.class
            );
        } catch (Exception e) {
            // 容错降级
        }
        stats.put("lifeguardCount", lifeguardCount != null ? lifeguardCount : 0);

        return stats;
    }

    @Override
    public Map<String, Object> getAlertTrends(Integer days) {
        if (days == null || days <= 0) {
            days = 7; // 默认7天趋势
        }
        Map<String, Object> trends = new HashMap<>();
        List<String> dates = new ArrayList<>();
        List<Long> alertCounts = new ArrayList<>();
        List<Long> rescueCounts = new ArrayList<>();

        LocalDate now = LocalDate.now();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate targetDate = now.minusDays(i);
            dates.add(targetDate.toString());

            LocalDateTime startOfDay = LocalDateTime.of(targetDate, LocalTime.MIN);
            LocalDateTime endOfDay = LocalDateTime.of(targetDate, LocalTime.MAX);

            // 统计单日报警
            Long alertCount = alertEventMapper.selectCount(new LambdaQueryWrapper<AlertEvent>()
                    .ge(AlertEvent::getCreatedAt, startOfDay)
                    .le(AlertEvent::getCreatedAt, endOfDay)
                    .eq(AlertEvent::getDeleted, 0));
            alertCounts.add(alertCount);

            // 统计单日完成救援
            Long rescueCount = rescueTaskMapper.selectCount(new LambdaQueryWrapper<RescueTask>()
                    .ge(RescueTask::getCompleteAt, startOfDay)
                    .le(RescueTask::getCompleteAt, endOfDay)
                    .eq(RescueTask::getDeleted, 0));
            rescueCounts.add(rescueCount);
        }

        trends.put("dates", dates);
        trends.put("alertCounts", alertCounts);
        trends.put("rescueCounts", rescueCounts);

        return trends;
    }

    @Override
    public Map<String, Object> getWaterAreaStats() {
        Map<String, Object> result = new HashMap<>();

        // 通过自定义 SQL 查询各水域的累计报警次数
        // 由于没有单独写复杂的 Join，直接聚合查询 alert_event 中 water_area_id 占比
        // 这一步可以使用 jdbcTemplate 或者 mybatis 写原生 map
        // 简单使用 mybatis plus 进行粗略统计
        List<AlertEvent> events = alertEventMapper.selectList(new LambdaQueryWrapper<AlertEvent>()
                .eq(AlertEvent::getDeleted, 0));

        Map<Long, Long> counts = new HashMap<>();
        for (AlertEvent ev : events) {
            if (ev.getWaterAreaId() != null) {
                counts.put(ev.getWaterAreaId(), counts.getOrDefault(ev.getWaterAreaId(), 0L) + 1);
            }
        }

        result.put("distribution", counts);
        return result;
    }
}
