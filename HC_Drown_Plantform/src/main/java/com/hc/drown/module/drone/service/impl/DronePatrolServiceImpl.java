package com.hc.drown.module.drone.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hc.drown.common.exception.BusinessException;
import com.hc.drown.common.util.GeoUtils;
import com.hc.drown.infrastructure.kafka.KafkaProducerService;
import com.hc.drown.module.device.entity.Device;
import com.hc.drown.module.device.service.DeviceService;
import com.hc.drown.module.drone.entity.DroneFlightLog;
import com.hc.drown.module.drone.entity.DroneMission;
import com.hc.drown.module.drone.entity.DronePlan;
import com.hc.drown.module.drone.entity.DroneWaypoint;
import com.hc.drown.module.drone.mapper.DroneFlightLogMapper;
import com.hc.drown.module.drone.mapper.DroneMissionMapper;
import com.hc.drown.module.drone.mapper.DronePlanMapper;
import com.hc.drown.module.drone.mapper.DroneWaypointMapper;
import com.hc.drown.module.drone.service.DronePatrolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DronePatrolServiceImpl extends ServiceImpl<DronePlanMapper, DronePlan> implements DronePatrolService {

    private final DroneWaypointMapper waypointMapper;
    private final DroneMissionMapper missionMapper;
    private final DroneFlightLogMapper flightLogMapper;
    private final DeviceService deviceService;
    private final KafkaProducerService kafkaProducerService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${hc.drown.kafka.topics.drone-command}")
    private String droneCommandTopic;

    private static final String REDIS_KEY_DRONE_TELEMETRY = "hc:drone:telemetry:";

    @Override
    @Transactional
    public boolean createPlan(DronePlan plan) {
        // 1. 保存主计划
        plan.setStatus("ACTIVE");
        plan.setCreatedAt(LocalDateTime.now());
        plan.setUpdatedAt(LocalDateTime.now());
        plan.setDeleted(0);
        int rows = baseMapper.insert(plan);

        // 2. 保存航路点
        List<DroneWaypoint> waypoints = plan.getWaypoints();
        if (waypoints != null && !waypoints.isEmpty()) {
            for (int i = 0; i < waypoints.size(); i++) {
                DroneWaypoint wp = waypoints.get(i);
                wp.setPlanId(plan.getId());
                wp.setSeqNo(i + 1);

                String wkt = GeoUtils.toWktPoint(wp.getLongitude(), wp.getLatitude());
                wp.setLocation(wkt);
                if (wp.getAltitude() == null) {
                    wp.setAltitude(plan.getFlightAltitude());
                }

                waypointMapper.insertWaypoint(wp);
            }
        }
        return rows > 0;
    }

    @Override
    public DronePlan getPlanById(Long id) {
        DronePlan plan = baseMapper.selectById(id);
        if (plan != null) {
            List<DroneWaypoint> waypoints = waypointMapper.selectByPlanId(id);
            for (DroneWaypoint wp : waypoints) {
                double[] coords = GeoUtils.parseWktPoint(wp.getLocation());
                if (coords != null) {
                    wp.setLongitude(coords[0]);
                    wp.setLatitude(coords[1]);
                }
            }
            plan.setWaypoints(waypoints);
        }
        return plan;
    }

    @Override
    @Transactional
    public DroneMission triggerEmergencyTakeoff(Long alertEventId, Long hangarId, Long operatorId) {
        // 1. 查询机库绑定的无人机 (假设对应机库下有一台ONLINE状态的DRONE设备)
        List<Device> drones = deviceService.list(new LambdaQueryWrapper<Device>()
                .eq(Device::getDeviceType, "DRONE")
                .eq(Device::getWaterAreaId, deviceService.getById(hangarId).getWaterAreaId())
                .eq(Device::getStatus, "ONLINE")
                .eq(Device::getDeleted, 0));

        if (drones.isEmpty()) {
            throw new BusinessException("该机库下暂无在线的无人机，无法触发调度起飞！");
        }

        Device targetDrone = drones.get(0);

        // 2. 创建巡航指令并写入数据库
        DroneMission mission = new DroneMission();
        mission.setMissionNo("DM" + IdUtil.getSnowflakeNextIdStr());
        mission.setDroneId(targetDrone.getId());
        mission.setTriggerType(alertEventId != null ? "EMERGENCY" : "MANUAL");
        mission.setTriggerEventId(alertEventId);
        mission.setStatus("PENDING");
        mission.setStartAt(LocalDateTime.now());
        mission.setOperatorId(operatorId);
        mission.setCreatedAt(LocalDateTime.now());
        mission.setUpdatedAt(LocalDateTime.now());
        mission.setDeleted(0);

        missionMapper.insert(mission);

        // 3. 发布 Kafka 指导无人机起飞
        Map<String, Object> command = new HashMap<>();
        command.put("missionId", mission.getId());
        command.put("missionNo", mission.getMissionNo());
        command.put("droneCode", targetDrone.getDeviceCode());
        command.put("command", "TAKEOFF");
        command.put("timestamp", System.currentTimeMillis());

        kafkaProducerService.sendMessage(droneCommandTopic, targetDrone.getDeviceCode(), command);
        log.info("Sent TAKE_OFF command to DroneCode: {} via Kafka", targetDrone.getDeviceCode());

        return mission;
    }

    @Override
    public Page<DroneMission> pageMissions(Integer pageNum, Integer pageSize, String status, Long droneId) {
        Page<DroneMission> page = new Page<>(pageNum, pageSize);
        return missionMapper.selectMissionPage(page, status, droneId);
    }

    @Override
    @Transactional
    public boolean uploadTelemetry(DroneFlightLog telemetry, Double lng, Double lat) {
        String wkt = GeoUtils.toWktPoint(lng, lat);
        telemetry.setLocation(wkt);

        // 1. 写入飞行流水日志
        flightLogMapper.insertFlightLog(telemetry);

        // 2. 缓存最新遥测到 Redis
        Map<String, Object> cache = new HashMap<>();
        cache.put("missionId", telemetry.getMissionId());
        cache.put("lng", lng);
        cache.put("lat", lat);
        cache.put("altitude", telemetry.getAltitude());
        cache.put("speed", telemetry.getSpeed());
        cache.put("batteryPct", telemetry.getBatteryPct());
        cache.put("loggedAt", LocalDateTime.now().toString());

        redisTemplate.opsForValue().set(
                REDIS_KEY_DRONE_TELEMETRY + telemetry.getMissionId(),
                cache,
                1, TimeUnit.HOURS
        );

        // 3. 更新无人机任务状态 (如果是第一次遥测，将 PENDING 改为 FLYING)
        DroneMission mission = missionMapper.selectById(telemetry.getMissionId());
        if (mission != null && "PENDING".equals(mission.getStatus())) {
            mission.setStatus("FLYING");
            mission.setUpdatedAt(LocalDateTime.now());
            missionMapper.updateById(mission);
        }

        return true;
    }

    @Override
    public List<DroneFlightLog> getTelemetryHistory(Long missionId) {
        return flightLogMapper.selectByMissionId(missionId);
    }

    @Override
    @Transactional
    public boolean endMission(Long missionId, Long operatorId) {
        DroneMission mission = missionMapper.selectById(missionId);
        if (mission == null || mission.getDeleted() == 1) {
            throw new BusinessException("该巡航任务不存在");
        }

        mission.setStatus("RETURNING");
        mission.setEndAt(LocalDateTime.now());
        mission.setUpdatedAt(LocalDateTime.now());
        missionMapper.updateById(mission);

        // 获取对应的 DroneCode 设备
        Device drone = deviceService.getById(mission.getDroneId());

        // 发送 Kafka 返航指令
        Map<String, Object> command = new HashMap<>();
        command.put("missionId", mission.getId());
        command.put("missionNo", mission.getMissionNo());
        command.put("droneCode", drone.getDeviceCode());
        command.put("command", "LAND_AND_RETURN");
        command.put("timestamp", System.currentTimeMillis());

        kafkaProducerService.sendMessage(droneCommandTopic, drone.getDeviceCode(), command);
        log.info("Sent LAND_AND_RETURN command to DroneCode: {}", drone.getDeviceCode());

        return true;
    }
}
