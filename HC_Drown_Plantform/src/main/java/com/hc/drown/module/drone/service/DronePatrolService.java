package com.hc.drown.module.drone.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hc.drown.module.drone.entity.DroneFlightLog;
import com.hc.drown.module.drone.entity.DroneMission;
import com.hc.drown.module.drone.entity.DronePlan;

import java.util.List;

public interface DronePatrolService extends IService<DronePlan> {

    /**
     * 创建巡航计划 (UC08)
     */
    boolean createPlan(DronePlan plan);

    /**
     * 获取巡航计划详情 (UC08)
     */
    DronePlan getPlanById(Long id);

    /**
     * 手动调度无人机应急起飞 (UC04 / F-15)
     * @param alertEventId 关联的警报事件ID (应急起飞时传)
     * @param hangarId 指派机库ID
     * @param operatorId 调度值守员ID
     */
    DroneMission triggerEmergencyTakeoff(Long alertEventId, Long hangarId, Long operatorId);

    /**
     * 分页查询巡航任务记录
     */
    Page<DroneMission> pageMissions(Integer pageNum, Integer pageSize, String status, Long droneId);

    /**
     * 上报无人机飞行实时遥测日志 (F-14)
     */
    boolean uploadTelemetry(DroneFlightLog telemetry, Double lng, Double lat);

    /**
     * 获取指定任务的遥测日志历史 (绘制轨迹)
     */
    List<DroneFlightLog> getTelemetryHistory(Long missionId);

    /**
     * 结束任务并指派返航 (F-15)
     */
    boolean endMission(Long missionId, Long operatorId);
}
