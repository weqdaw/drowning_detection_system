package com.hc.drown.module.alert.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hc.drown.common.exception.BusinessException;
import com.hc.drown.common.util.GeoUtils;
import com.hc.drown.infrastructure.kafka.KafkaProducerService;
import com.hc.drown.module.alert.entity.AlertEvent;
import com.hc.drown.module.alert.mapper.AlertEventMapper;
import com.hc.drown.module.alert.service.AlertEventService;
import com.hc.drown.module.device.entity.WaterArea;
import com.hc.drown.module.device.service.WaterAreaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hc.drown.module.device.service.StreamDistributionService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertEventServiceImpl extends ServiceImpl<AlertEventMapper, AlertEvent> implements AlertEventService {

    private final WaterAreaService waterAreaService;
    private final KafkaProducerService kafkaProducerService;
    private final StreamDistributionService streamDistributionService;
    private final org.springframework.beans.factory.ObjectProvider<com.hc.drown.module.task.service.RescueTaskService> rescueTaskServiceProvider;

    @Value("${hc.drown.kafka.topics.alert-event}")
    private String alertEventTopic;

    @Value("${hc.drown.kafka.topics.alert-confirmed}")
    private String alertConfirmedTopic;

    @Override
    public Page<AlertEvent> pageAlertEvents(Integer pageNum, Integer pageSize, String status, Long waterAreaId, String alertType) {
        Page<AlertEvent> page = new Page<>(pageNum, pageSize);
        return baseMapper.selectAlertEventPage(page, status, waterAreaId, alertType);
    }

    @Override
    public AlertEvent getAlertEventById(Long id) {
        return baseMapper.selectAlertEventById(id);
    }

    @Override
    @Transactional
    public AlertEvent receiveAlert(AlertEvent event) {
        // 1. 生成事件单号与默认属性
        event.setEventNo("AE" + IdUtil.getSnowflakeNextIdStr());
        event.setStatus("PENDING");
        event.setCreatedAt(LocalDateTime.now());
        event.setUpdatedAt(LocalDateTime.now());
        event.setDeleted(0);

        // 2. 依据经纬度反查水域 ID
        if (event.getLongitude() != null && event.getLatitude() != null) {
            String wktPoint = GeoUtils.toWktPoint(event.getLongitude(), event.getLatitude());
            event.setAlertLocation(wktPoint);

            List<WaterArea> waterAreas = waterAreaService.findContainingPoint(event.getLongitude(), event.getLatitude());
            if (!waterAreas.isEmpty()) {
                event.setWaterAreaId(waterAreas.get(0).getId());
            }
        }

        // 3. 写入数据库
        baseMapper.insertAlertEvent(event);

        // 4. 发送 Kafka 异步消息通知前端大屏及监听器
        // 重新拉取以包含设备和水域名称
        AlertEvent enrichedEvent = baseMapper.selectAlertEventById(event.getId());
        kafkaProducerService.sendMessage(alertEventTopic, enrichedEvent.getEventNo(), enrichedEvent);

        // 5. 自动同步产生救援任务单（针对溺水/危险报警事件）
        try {
            rescueTaskServiceProvider.ifAvailable(service -> service.dispatchTask(enrichedEvent.getId(), null));
        } catch (Exception e) {
            log.warn("自动产生救援任务单通知微异常: {}", e.getMessage());
        }

        return enrichedEvent;
    }

    @Override
    @Transactional
    public boolean confirmAlert(Long id, boolean isReal, String reason, Long userId) {
        AlertEvent event = baseMapper.selectById(id);
        if (event == null || event.getDeleted() == 1) {
            throw new BusinessException("该报警事件不存在");
        }
        if (!"PENDING".equals(event.getStatus())) {
            throw new BusinessException("该报警事件已被处理，当前状态为: " + event.getStatus());
        }

        event.setConfirmedBy(userId);
        event.setConfirmedAt(LocalDateTime.now());
        event.setUpdatedAt(LocalDateTime.now());

        if (isReal) {
            event.setStatus("CONFIRMED");
        } else {
            event.setStatus("FALSE_ALARM");
            event.setFalseAlarmReason(reason);
        }

        // 重新保存
        baseMapper.updateAlertEvent(event);

        // 如果是真实溺水，发布确认消息到 Kafka 触发联动逻辑 (UAV、广播、派单)
        if (isReal) {
            AlertEvent enrichedEvent = baseMapper.selectAlertEventById(id);
            kafkaProducerService.sendMessage(alertConfirmedTopic, enrichedEvent.getEventNo(), enrichedEvent);
            log.info("Alert confirmed, sent event to Kafka topic: {}", alertConfirmedTopic);
        }

        return true;
    }

    @Override
    public List<Map<String, Object>> getDetectionHistory(Integer limit) {
        return streamDistributionService.getEvents(limit);
    }

    @Override
    public List<Map<String, Object>> getRecordings(String streamId, Integer limit) {
        return streamDistributionService.getRecordings(streamId, limit);
    }

    @Override
    public boolean deleteRecording(Integer id) {
        return streamDistributionService.deleteRecording(id);
    }

    @Override
    public boolean clearRecordings() {
        return streamDistributionService.deleteAllRecordings();
    }

    @Override
    public boolean deleteDetectionHistory(Integer id) {
        return streamDistributionService.deleteEvent(id);
    }

    @Override
    public boolean clearDetectionHistory() {
        return streamDistributionService.deleteAllEvents();
    }
}
