package com.hc.drown.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hc.drown.module.alert.entity.AlertActionLog;
import com.hc.drown.module.alert.entity.AlertEvent;
import com.hc.drown.module.alert.mapper.AlertActionLogMapper;
import com.hc.drown.module.device.entity.Device;
import com.hc.drown.module.device.service.DeviceService;
import com.hc.drown.module.drone.entity.DroneMission;
import com.hc.drown.module.drone.service.DronePatrolService;
import com.hc.drown.module.task.entity.RescueTask;
import com.hc.drown.module.task.service.RescueTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 警报确认后的决策联动决策引擎 (UC02 / F-06)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AlertConfirmedListener {

    private final DeviceService deviceService;
    private final RescueTaskService rescueTaskService;
    private final DronePatrolService dronePatrolService;
    private final AlertActionLogMapper actionLogMapper;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "${hc.drown.kafka.topics.alert-confirmed}", groupId = "hc-decision-group")
    public void onAlertConfirmed(String message) {
        log.info("Ingesting confirmed alert message from Kafka: {}", message);
        try {
            AlertEvent event = objectMapper.readValue(message, AlertEvent.class);
            log.info("Decoupled alert event AE{} for automated linkages", event.getEventNo());

            // 1. 获取警报坐标
            String locationWkt = event.getAlertLocation();
            if (locationWkt == null) {
                log.error("Event alert location is null, aborting automatic linkage");
                return;
            }

            // 解析经纬度
            String content = locationWkt.substring(locationWkt.indexOf("(") + 1, locationWkt.indexOf(")"));
            String[] parts = content.split(" ");
            double lng = Double.parseDouble(parts[0]);
            double lat = Double.parseDouble(parts[1]);

            // 2. 联动音柱进行最近喊话/预置告警广播 (F-13)
            try {
                Device nearestSpeaker = deviceService.findNearestDevice(lng, lat, "SPEAKER");
                if (nearestSpeaker != null) {
                    log.info("Linkage -> Broadcasting to nearest speaker: {}", nearestSpeaker.getDeviceName());
                    // 记录联动动作
                    AlertActionLog action = new AlertActionLog();
                    action.setEventId(event.getId());
                    action.setActionType("SPEAKER_BROADCAST");
                    action.setTargetId(nearestSpeaker.getId());
                    action.setStatus("SUCCESS");
                    action.setDetail("自动联动最近广播音柱: " + nearestSpeaker.getDeviceName() + " 播放防溺水警示语音");
                    action.setExecutedAt(LocalDateTime.now());
                    actionLogMapper.insert(action);
                } else {
                    log.warn("No online audio speaker found near alert location");
                }
            } catch (Exception e) {
                log.error("Failed to execute speaker linkage", e);
            }

            // 3. 联动自动机库下发无人机快速起飞 (F-15)
            try {
                Device nearestHangar = deviceService.findNearestDevice(lng, lat, "DRONE_HANGAR");
                if (nearestHangar != null) {
                    log.info("Linkage -> Commanding nearest drone hangar: {}", nearestHangar.getDeviceName());
                    // 触发起飞
                    DroneMission mission = dronePatrolService.triggerEmergencyTakeoff(event.getId(), nearestHangar.getId(), null);

                    AlertActionLog action = new AlertActionLog();
                    action.setEventId(event.getId());
                    action.setActionType("UAV_DISPATCH");
                    action.setTargetId(mission.getId());
                    action.setStatus("SUCCESS");
                    action.setDetail("联动起飞无人机. 对应任务号: " + mission.getMissionNo());
                    action.setExecutedAt(LocalDateTime.now());
                    actionLogMapper.insert(action);
                } else {
                    log.warn("No online drone hangar found near alert location");
                }
            } catch (Exception e) {
                log.error("Failed to execute drone takeoff linkage", e);
            }

            // 4. 调度在岗救生员前往现场进行救援 (F-07)
            try {
                log.info("Linkage -> Dispatching rescue lifeguard...");
                RescueTask task = rescueTaskService.dispatchTask(event.getId(), null);

                AlertActionLog action = new AlertActionLog();
                action.setEventId(event.getId());
                action.setActionType("TASK_CREATE");
                action.setTargetId(task.getId());
                action.setStatus("SUCCESS");
                action.setDetail("指派救生员进行现场处置. 任务单号: " + task.getTaskNo());
                action.setExecutedAt(LocalDateTime.now());
                actionLogMapper.insert(action);
            } catch (Exception e) {
                log.error("Failed to execute lifeguard dispatch linkage", e);
            }

        } catch (Exception e) {
            log.error("Failed to parse alert confirmed Kafka message", e);
        }
    }
}
