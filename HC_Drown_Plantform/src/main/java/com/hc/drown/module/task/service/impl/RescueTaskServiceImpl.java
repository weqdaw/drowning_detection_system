package com.hc.drown.module.task.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hc.drown.common.exception.BusinessException;
import com.hc.drown.common.util.GeoUtils;
import com.hc.drown.infrastructure.kafka.KafkaProducerService;
import com.hc.drown.module.alert.entity.AlertEvent;
import com.hc.drown.module.alert.service.AlertEventService;
import com.hc.drown.module.task.entity.LifeguardLocation;
import com.hc.drown.module.task.entity.RescueTask;
import com.hc.drown.module.task.entity.RescueTaskLog;
import com.hc.drown.module.task.mapper.LifeguardLocationMapper;
import com.hc.drown.module.task.mapper.RescueTaskLogMapper;
import com.hc.drown.module.task.mapper.RescueTaskMapper;
import com.hc.drown.module.task.service.RescueTaskService;
import com.hc.drown.module.user.entity.SysUser;
import com.hc.drown.module.user.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RescueTaskServiceImpl extends ServiceImpl<RescueTaskMapper, RescueTask> implements RescueTaskService {

    private final AlertEventService alertEventService;
    private final SysUserService userService;
    private final RescueTaskLogMapper taskLogMapper;
    private final LifeguardLocationMapper lifeguardLocationMapper;
    private final KafkaProducerService kafkaProducerService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${hc.drown.kafka.topics.rescue-task}")
    private String rescueTaskTopic;

    @Value("${hc.drown.dispatch.max-rescuer-count}")
    private Integer maxRescuerCount;

    private static final String REDIS_KEY_LIFEGUARD_LOCATION = "hc:lifeguard:location:";

    @Override
    public Page<RescueTask> pageRescueTasks(Integer pageNum, Integer pageSize, String status, Long assigneeId, Long eventId) {
        Page<RescueTask> page = new Page<>(pageNum, pageSize);
        return baseMapper.selectRescueTaskPage(page, status, assigneeId, eventId);
    }

    @Override
    public RescueTask getRescueTaskById(Long id) {
        return baseMapper.selectRescueTaskById(id);
    }

    @Override
    @Transactional
    public RescueTask dispatchTask(Long eventId, Long dispatcherId) {
        AlertEvent event = alertEventService.getById(eventId);
        if (event == null || event.getDeleted() == 1) {
            throw new BusinessException("绑定的报警事件不存在");
        }

        // 解析警报的 WKTPoint 经纬度（若为空或格式非标，降级为系统默认水域坐标，防止中断派单落库）
        double[] coords = GeoUtils.parseWktPoint(event.getAlertLocation());
        if (coords == null) {
            coords = new double[]{120.0318156, 28.5093682};
            event.setAlertLocation("POINT(120.0318156 28.5093682)");
            alertEventService.updateById(event);
            log.warn("警报事件 AE{} 坐标无效，已降级修复为默认水域坐标 POINT(120.0318156 28.5093682)", event.getEventNo());
        }

        // 0. 幂等查重：如果该警报事件已创建过救援任务单，直接返回已有任务单
        List<RescueTask> existingTasks = baseMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RescueTask>()
                .eq(RescueTask::getEventId, eventId)
                .eq(RescueTask::getDeleted, 0));
        if (!existingTasks.isEmpty()) {
            return baseMapper.selectRescueTaskById(existingTasks.get(0).getId());
        }

        // 1. 查询最近的在岗救生员
        List<SysUser> lifeguards = Collections.emptyList();
        try {
            lifeguards = lifeguardLocationMapper.findActiveLifeguards();
        } catch (Exception e) {
            log.warn("查询在线救生员打点发生小幅异常，启动自动降级方案: {}", e.getMessage());
        }
        SysUser targetRescuer = null;

        if (lifeguards != null && !lifeguards.isEmpty()) {
            // 在 Java 层计算距离并选择最近的在岗救生员
            double minDistance = Double.MAX_VALUE;
            for (SysUser rescuer : lifeguards) {
                if (Integer.valueOf(1).equals(rescuer.getDutyStatus()) && rescuer.getLastWktLocation() != null) {
                    double[] rescuerCoords = GeoUtils.parseWktPoint(rescuer.getLastWktLocation());
                    if (rescuerCoords != null) {
                        double dist = GeoUtils.calculateDistance(coords[0], coords[1], rescuerCoords[0], rescuerCoords[1]);
                        if (dist < minDistance) {
                            minDistance = dist;
                            targetRescuer = rescuer;
                        }
                    }
                }
            }
            if (targetRescuer == null) {
                targetRescuer = lifeguards.stream()
                        .filter(u -> Integer.valueOf(1).equals(u.getDutyStatus()))
                        .findFirst()
                        .orElse(lifeguards.get(0));
            }
        } else {
            // 降级防错：若当前无打卡在岗救生员，查询任意救生员或系统管理员，确保任务单可以成功落库
            log.warn("No lifeguards with location on duty for event AE{}, falling back to default admin/user", event.getEventNo());
            List<SysUser> allUsers = userService.list();
            if (!allUsers.isEmpty()) {
                targetRescuer = allUsers.stream()
                        .filter(u -> u.getDeleted() == null || u.getDeleted() == 0)
                        .findFirst()
                        .orElse(allUsers.get(0));
            }
            if (targetRescuer == null) {
                targetRescuer = new SysUser();
                targetRescuer.setId(1L);
                targetRescuer.setRealName("系统管理员");
            }
        }

        // 2. 创建救援任务
        RescueTask task = new RescueTask();
        task.setTaskNo("RT" + IdUtil.getSnowflakeNextIdStr());
        task.setEventId(eventId);
        task.setAssigneeId(targetRescuer.getId());
        task.setDispatcherId(dispatcherId);
        task.setStatus("PENDING");
        task.setTaskLocation(event.getAlertLocation());
        task.setPriority(2); // 紧急
        task.setDispatchAt(LocalDateTime.now());
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        task.setDeleted(0);

        baseMapper.insertRescueTask(task);

        // 3. 记录日志
        RescueTaskLog logEntry = new RescueTaskLog();
        logEntry.setTaskId(task.getId());
        logEntry.setOperatorId(dispatcherId);
        logEntry.setAction("DISPATCH");
        logEntry.setNote("系统自动指派任务给救生员: " + targetRescuer.getRealName());
        logEntry.setLocation(event.getAlertLocation());
        logEntry.setLoggedAt(LocalDateTime.now());
        taskLogMapper.insert(logEntry);

        // 4. 发送 Kafka 派单消息 (触发 App 推送)
        RescueTask enrichedTask = baseMapper.selectRescueTaskById(task.getId());
        kafkaProducerService.sendMessage(rescueTaskTopic, enrichedTask.getTaskNo(), enrichedTask);

        log.info("Successfully dispatched RT{} to lifeguard: {}", enrichedTask.getTaskNo(), targetRescuer.getRealName());
        return enrichedTask;
    }

    @Override
    @Transactional
    public boolean acceptTask(Long taskId, Long userId) {
        RescueTask task = baseMapper.selectById(taskId);
        if (task == null || task.getDeleted() == 1) {
            throw new BusinessException("救援任务不存在");
        }
        if (!"DISPATCHED".equals(task.getStatus())) {
            throw new BusinessException("救援任务已被其他处理或关闭，当前状态: " + task.getStatus());
        }

        task.setStatus("ACCEPTED");
        task.setAcceptAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        baseMapper.updateRescueTask(task);

        // 写入任务操作日志
        RescueTaskLog logEntry = new RescueTaskLog();
        logEntry.setTaskId(taskId);
        logEntry.setOperatorId(userId);
        logEntry.setAction("ACCEPT");
        logEntry.setNote("救生员已接受救援任务");
        logEntry.setLoggedAt(LocalDateTime.now());
        taskLogMapper.insert(logEntry);

        return true;
    }

    @Override
    @Transactional
    public boolean arriveTask(Long taskId, Long userId, Double lng, Double lat) {
        RescueTask task = baseMapper.selectById(taskId);
        if (task == null || task.getDeleted() == 1) {
            throw new BusinessException("救援任务不存在");
        }
        if (!"ACCEPTED".equals(task.getStatus())) {
            throw new BusinessException("任务状态错误，必须是已接受状态，当前: " + task.getStatus());
        }

        task.setStatus("ARRIVED");
        task.setArriveAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        baseMapper.updateRescueTask(task);

        // 记录操作日志
        RescueTaskLog logEntry = new RescueTaskLog();
        logEntry.setTaskId(taskId);
        logEntry.setOperatorId(userId);
        logEntry.setAction("ARRIVE");
        logEntry.setNote("救生员已到达现场");
        if (lng != null && lat != null) {
            logEntry.setLocation(GeoUtils.toWktPoint(lng, lat));
        }
        logEntry.setLoggedAt(LocalDateTime.now());
        taskLogMapper.insert(logEntry);

        return true;
    }

    @Override
    @Transactional
    public boolean completeTask(Long taskId, Long userId, String resultType, String note, List<String> photoUrls) {
        RescueTask task = baseMapper.selectById(taskId);
        if (task == null || task.getDeleted() == 1) {
            throw new BusinessException("救援任务不存在");
        }

        task.setStatus("COMPLETED");
        task.setCompleteAt(LocalDateTime.now());
        task.setResultType(resultType);
        task.setResultNote(note);
        task.setResultPhotoUrls(photoUrls);
        task.setUpdatedAt(LocalDateTime.now());
        baseMapper.updateRescueTask(task);

        // 1. 记录日志
        RescueTaskLog logEntry = new RescueTaskLog();
        logEntry.setTaskId(taskId);
        logEntry.setOperatorId(userId);
        logEntry.setAction("COMPLETE");
        logEntry.setNote("完成救援任务: " + note);
        logEntry.setLoggedAt(LocalDateTime.now());
        taskLogMapper.insert(logEntry);

        // 2. 联动关闭报警事件状态
        AlertEvent event = alertEventService.getById(task.getEventId());
        if (event != null && !"RESOLVED".equals(event.getStatus())) {
            event.setStatus("RESOLVED");
            event.setResolvedAt(LocalDateTime.now());
            event.setUpdatedAt(LocalDateTime.now());
            alertEventService.updateById(event);
            log.info("Closed related alert event AE{}", event.getEventNo());
        }

        return true;
    }

    @Override
    @Transactional
    public boolean uploadLocation(LifeguardLocation location, Double lng, Double lat) {
        // 1. 解析/构造 PostGIS point
        String wktPoint = GeoUtils.toWktPoint(lng, lat);
        location.setLocation(wktPoint);

        // 2. 插入地理记录表
        lifeguardLocationMapper.insertLocation(location);

        // 3. 缓存在 Redis (2小时有效，提供快速地图态势渲染)
        try {
            Map<String, Object> cacheMap = new HashMap<>();
            cacheMap.put("userId", location.getUserId());
            cacheMap.put("lng", lng);
            cacheMap.put("lat", lat);
            cacheMap.put("recordedAt", LocalDateTime.now().toString());
            cacheMap.put("dutyStatus", location.getDutyStatus());

            redisTemplate.opsForValue().set(
                    REDIS_KEY_LIFEGUARD_LOCATION + location.getUserId(),
                    cacheMap,
                    2, TimeUnit.HOURS
            );
        } catch (Exception e) {
            log.warn("Redis 位置缓存更新异常 (防崩溃自动捕获): {}", e.getMessage());
        }

        return true;
    }

    @Override
    @Transactional
    public boolean updateTaskStatus(Long taskId, String status, Long userId, String note) {
        RescueTask task = baseMapper.selectById(taskId);
        if (task == null || task.getDeleted() == 1) {
            throw new BusinessException("救援任务不存在");
        }

        String targetStatus = status != null ? status.toUpperCase() : "PROCESSED";
        if ("PROCESSED".equals(targetStatus) || "COMPLETED".equals(targetStatus) || "RESOLVED".equals(targetStatus)) {
            task.setStatus("PROCESSED");
            task.setCompleteAt(LocalDateTime.now());
        } else if ("RECOGNITION_ERROR".equals(targetStatus) || "FALSE_ALARM".equals(targetStatus)) {
            task.setStatus("RECOGNITION_ERROR");
            task.setResultType("FALSE_ALARM");
        } else {
            task.setStatus(targetStatus);
        }

        if (note != null) {
            task.setResultNote(note);
        }
        task.setUpdatedAt(LocalDateTime.now());
        baseMapper.updateRescueTask(task);

        // 1. 记录任务改状态日志
        RescueTaskLog logEntry = new RescueTaskLog();
        logEntry.setTaskId(taskId);
        logEntry.setOperatorId(userId != null ? userId : 1L);
        logEntry.setAction("UPDATE_STATUS");
        logEntry.setNote("控制台人工标注任务状态为: " + targetStatus + (note != null ? (" (" + note + ")") : ""));
        logEntry.setLoggedAt(LocalDateTime.now());
        taskLogMapper.insert(logEntry);

        // 2. 联动更新事件中心 AlertEvent 状态
        if (task.getEventId() != null) {
            AlertEvent event = alertEventService.getById(task.getEventId());
            if (event != null) {
                if ("PROCESSED".equals(targetStatus) || "COMPLETED".equals(targetStatus) || "RESOLVED".equals(targetStatus)) {
                    event.setStatus("RESOLVED");
                    event.setResolvedAt(LocalDateTime.now());
                } else if ("RECOGNITION_ERROR".equals(targetStatus) || "FALSE_ALARM".equals(targetStatus)) {
                    event.setStatus("FALSE_ALARM");
                    event.setFalseAlarmReason(note != null ? note : "控制台人工标注识别错误");
                }
                event.setUpdatedAt(LocalDateTime.now());
                alertEventService.updateById(event);
            }
        }

        log.info("Task #{} status updated to {}", taskId, targetStatus);
        return true;
    }
}
