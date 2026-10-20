package com.hc.drown.module.device.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hc.drown.module.alert.entity.AlertEvent;
import com.hc.drown.module.alert.service.AlertEventService;
import com.hc.drown.module.device.entity.Device;
import com.hc.drown.module.device.service.DeviceService;
import com.hc.drown.module.device.service.StreamDistributionService;
import com.hc.drown.module.device.service.StreamDistributionService.StreamData;
import com.hc.drown.module.device.service.StreamDistributionService.Detection;
import com.hc.drown.module.device.service.StreamDistributionService.StreamInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 监控分发系统定时同步调度器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StreamDistributionScheduler {

    private final StreamDistributionService streamDistributionService;
    private final DeviceService deviceService;
    private final AlertEventService alertEventService;
    private final com.hc.drown.module.task.service.RescueTaskService rescueTaskService;

    // Seed for simulation
    private final Random random = new Random();

    /**
     * 每3秒同步一次实时监控流的AI推理结果与状态
     */
    @Scheduled(fixedDelay = 3000)
    public void syncDistributionData() {
        // 1. 获取视频处理平台中的所有监控流通道列表
        List<StreamInfo> streamInfoList = streamDistributionService.getStreams();
        if (streamInfoList == null) {
            // API 请求失败（例如 Python 后端尚未启动或网络异常），不执行硬性增删，降级为仿真模式
            List<Device> cameras = deviceService.getDevicesByType("CAMERA");
            if (!cameras.isEmpty()) {
                processSimulatedData(cameras);
            }
            return;
        }

        // 2. 获取实时检测数据
        List<StreamData> streamDataList = streamDistributionService.getRealtimeData();
        Map<String, StreamData> streamDataMap = new HashMap<>();
        if (streamDataList != null) {
            for (StreamData data : streamDataList) {
                if (data.getStreamId() != null) {
                    streamDataMap.put(data.getStreamId().toLowerCase(), data);
                }
            }
        }

        // 3. 获取大屏端现有的所有未删除的摄像头
        List<Device> cameras = deviceService.getDevicesByType("CAMERA");

        // 4. 同步大屏端摄像头与视频平台的直播流
        Set<String> activeStreamIds = new HashSet<>();
        for (StreamInfo stream : streamInfoList) {
            if (stream.getId() == null) continue;
            String streamId = stream.getId();
            activeStreamIds.add(streamId.toLowerCase());

            // 检查数据库中是否存在该摄像头 (不论是否deleted)
            Device device = deviceService.getDeviceByCodeIncludeDeleted(streamId);

            boolean isRunning = "Running".equalsIgnoreCase(stream.getStatus());
            String newStatus = isRunning ? "ONLINE" : "OFFLINE";

            if (device != null) {
                // 如果是已删除的，恢复它
                if (device.getDeleted() != null && device.getDeleted() == 1) {
                    device.setDeleted(0);
                    log.info("从视频分发系统重新发现通道，恢复大屏摄像头设备: {}", streamId);
                }
                device.setDeviceName(stream.getName());
                device.setStatus(newStatus);
                device.setLastHeartbeat(LocalDateTime.now());
                device.setUpdatedAt(LocalDateTime.now());

                // 更新额外配置
                Map<String, Object> config = device.getExtraConfig();
                if (config == null) {
                    config = new HashMap<>();
                }

                // 如果当前正在分析运行，且有实时数据，则更新推理 Fps 等数据
                StreamData realData = streamDataMap.get(streamId.toLowerCase());
                if (realData != null) {
                    config.put("selectedModel", realData.getSelectedModel());
                    config.put("inferenceFps", realData.getInferenceFps());
                    config.put("decodeFps", realData.getDecodeFps());
                    config.put("device", realData.getDevice());
                    config.put("counts", realData.getCounts());
                    config.put("detections", realData.getDetections());
                    config.put("timestamp", realData.getTimestamp());
                } else {
                    config.put("selectedModel", stream.getSelectedModel());
                    config.put("inferenceFps", stream.getInferenceFps() != null ? stream.getInferenceFps() : 0);
                    config.put("decodeFps", stream.getDecodeFps() != null ? stream.getDecodeFps() : 0);
                    config.put("device", stream.getDevice());
                    config.put("counts", new HashMap<>());
                    config.put("detections", new ArrayList<>());
                    config.put("timestamp", LocalDateTime.now().toString());
                }

                // 如果是在线运行状态，播放地址设为 ZLM 推推理流地址，否则设为原始 inputUrl
                if (isRunning) {
                    config.put("streamUrl", "rtsp://127.0.0.1:554/live/" + streamId + "_yolo");
                } else {
                    config.put("streamUrl", stream.getInputUrl());
                }
                device.setExtraConfig(config);

                deviceService.updateDevice(device);

                // 检查是否检测到风险目标 (Drowning, person, etc.) - 完全由 AI 模型认定决定触发
                if (realData != null && realData.getDetections() != null && !realData.getDetections().isEmpty()) {
                    for (Detection det : realData.getDetections()) {
                        String clsName = det.getClassName() != null ? det.getClassName() : "";
                        boolean isRiskClass = "Drowning".equalsIgnoreCase(clsName) || "drowing".equalsIgnoreCase(clsName) || "drowning".equalsIgnoreCase(clsName) || "person".equalsIgnoreCase(clsName) || "people".equalsIgnoreCase(clsName) || "fall".equalsIgnoreCase(clsName) || "Freestyle".equalsIgnoreCase(clsName) || "backstroke".equalsIgnoreCase(clsName) || "breaststroke".equalsIgnoreCase(clsName) || "diving".equalsIgnoreCase(clsName) || "standing".equalsIgnoreCase(clsName);

                        if (isRiskClass) {
                            double confVal = det.getConfidence() != null ? det.getConfidence() : 0.85;
                            triggerAlertIfNeeded(device, confVal, clsName, null);
                            break;
                        }
                    }
                }

            } else {
                // 不存在，创建新的摄像头设备
                Device newDevice = new Device();
                newDevice.setDeviceCode(streamId);
                newDevice.setDeviceName(stream.getName());
                newDevice.setDeviceType("CAMERA");
                newDevice.setManufacturer("AI_PLATFORM");
                newDevice.setModel("YOLO_AGENT");

                // 解析 IP
                String ip = "127.0.0.1";
                if (stream.getInputUrl() != null) {
                    try {
                        java.net.URI uri = java.net.URI.create(stream.getInputUrl().replace("rtsp://", "http://"));
                        if (uri.getHost() != null) {
                            ip = uri.getHost();
                        }
                    } catch (Exception ignored) {}
                }
                newDevice.setIpAddress(ip);
                newDevice.setPort(554);
                newDevice.setWaterAreaId(1L); // 默认重点区域
                newDevice.setLocation("POINT(120.0318156 28.5093682)"); // 默认村落地标坐标
                newDevice.setStatus(newStatus);
                newDevice.setLastHeartbeat(LocalDateTime.now());
                newDevice.setCreatedAt(LocalDateTime.now());
                newDevice.setUpdatedAt(LocalDateTime.now());
                newDevice.setDeleted(0);

                Map<String, Object> config = new HashMap<>();
                config.put("selectedModel", stream.getSelectedModel());
                config.put("inferenceFps", stream.getInferenceFps() != null ? stream.getInferenceFps() : 0);
                config.put("decodeFps", stream.getDecodeFps() != null ? stream.getDecodeFps() : 0);
                config.put("device", stream.getDevice());
                config.put("counts", new HashMap<>());
                config.put("detections", new ArrayList<>());

                if (isRunning) {
                    config.put("streamUrl", "rtsp://127.0.0.1:554/live/" + streamId + "_yolo");
                } else {
                    config.put("streamUrl", stream.getInputUrl());
                }
                newDevice.setExtraConfig(config);

                deviceService.createDevice(newDevice);
                log.info("同步到视频处理平台新接入的通道，创建大屏摄像头设备: {}", streamId);
            }
        }

        // 5. 对大屏端多余的摄像头进行软删除
        for (Device camera : cameras) {
            String code = camera.getDeviceCode();
            if (code != null && !activeStreamIds.contains(code.toLowerCase())) {
                camera.setDeleted(1);
                camera.setUpdatedAt(LocalDateTime.now());
                deviceService.updateDevice(camera);
                log.info("视频处理平台已删除该通道，同步软删除大屏摄像头设备: {}", code);
            }
        }

        // 6. 同步 AI 处理平台产生的高置信度抓拍录像清单与安全预警日志 -> 自动产生风险任务单
        syncRecordingsToTasks(cameras);
        syncEventsToTasks(cameras);
    }

    private final Set<Integer> processedPythonEventIds = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> processedRecordingFiles = Collections.synchronizedSet(new HashSet<>());

    private Device findMatchingDevice(String streamId, List<Device> cameras) {
        if (cameras == null || cameras.isEmpty()) {
            cameras = deviceService.getDevicesByType("CAMERA");
        }
        if (cameras == null || cameras.isEmpty()) return null;
        if (streamId == null) return cameras.get(0);

        String target = streamId.trim().toLowerCase();
        for (Device dev : cameras) {
            if (dev.getDeviceCode() != null && dev.getDeviceCode().toLowerCase().equals(target)) {
                return dev;
            }
        }
        for (Device dev : cameras) {
            if (dev.getDeviceCode() != null && (dev.getDeviceCode().toLowerCase().contains(target) || target.contains(dev.getDeviceCode().toLowerCase()))) {
                return dev;
            }
            if (dev.getDeviceName() != null && (dev.getDeviceName().toLowerCase().contains(target) || target.contains(dev.getDeviceName().toLowerCase()))) {
                return dev;
            }
        }
        return cameras.get(0);
    }

    private void syncRecordingsToTasks(List<Device> cameras) {
        try {
            List<Map<String, Object>> recordings = streamDistributionService.getRecordings(null, 20);
            if (recordings == null || recordings.isEmpty()) return;

            for (Map<String, Object> rec : recordings) {
                String fileName = (String) rec.get("file_name");
                if (fileName != null && processedRecordingFiles.contains(fileName)) {
                    continue;
                }

                String streamId = (String) rec.get("stream_id");
                Device dev = findMatchingDevice(streamId, cameras);
                if (dev == null) continue;

                Number maxConfNum = (Number) rec.get("max_confidence");
                double maxConf = maxConfNum != null ? maxConfNum.doubleValue() : 0.85;

                String snapshotUrl = fileName != null ? "/static/recordings/" + fileName : null;
                createTaskForPythonEvent(dev, maxConf, "Drowning", snapshotUrl);

                if (fileName != null) {
                    processedRecordingFiles.add(fileName);
                }
            }
        } catch (Exception e) {
            log.warn("同步抓拍录像到任务中心产生微小异常: {}", e.getMessage());
        }
    }

    private void syncEventsToTasks(List<Device> cameras) {
        try {
            List<Map<String, Object>> events = streamDistributionService.getEvents(30);
            if (events == null || events.isEmpty()) return;

            for (Map<String, Object> evt : events) {
                Object rawId = evt.get("id");
                Integer evtId = null;
                if (rawId instanceof Number) {
                    evtId = ((Number) rawId).intValue();
                }

                if (evtId != null && processedPythonEventIds.contains(evtId)) {
                    continue;
                }

                String streamId = (String) evt.get("stream_id");
                Device dev = findMatchingDevice(streamId, cameras);
                if (dev == null) continue;

                String ruleType = (String) evt.get("rule_type");
                String eventName = (String) evt.get("event_name");
                Number confNum = (Number) evt.get("confidence");
                double conf = confNum != null ? confNum.doubleValue() : 0.85;

                String details = (String) evt.get("details");
                String combined = (eventName != null ? eventName : "") + " " + (details != null ? details : "");
                boolean isAlertRule = ruleType != null && !ruleType.equalsIgnoreCase("detection");
                boolean isAudioOrDrowning = combined.contains("Audio") || combined.contains("Drowning") || combined.contains("drowing") || combined.contains("drowning") || combined.contains("告警") || combined.contains("事故") || combined.contains("落水") || combined.contains("溺水");

                if (isAlertRule || isAudioOrDrowning) {
                    createTaskForPythonEvent(dev, conf, eventName != null ? eventName : "Drowning", null);
                }

                if (evtId != null) {
                    processedPythonEventIds.add(evtId);
                }
            }
        } catch (Exception e) {
            log.warn("同步安全预警日志到任务中心产生微小异常: {}", e.getMessage());
        }
    }

    private void processSimulatedData(List<Device> cameras) {
        for (Device camera : cameras) {
            // 默认设置为在线
            camera.setStatus("ONLINE");
            camera.setLastHeartbeat(LocalDateTime.now());

            Map<String, Object> config = camera.getExtraConfig();
            if (config == null) {
                config = new HashMap<>();
            }

            String selectedModel = (String) config.getOrDefault("selectedModel", "best.pt");
            config.put("selectedModel", selectedModel);
            config.put("inferenceFps", 25);
            config.put("decodeFps", 25);
            config.put("device", "cuda");

            Map<String, Integer> counts = new HashMap<>();
            List<Map<String, Object>> detections = new ArrayList<>();

            // 仿真模式下提高模拟溺水事件概率 (30%)，确保在测试及无真实相机输入时能稳定持续在任务中心产生溺水任务
            boolean isDrowningSimulated = random.nextDouble() < 0.30;

            if (isDrowningSimulated) {
                counts.put("Drowning", 1);

                Map<String, Object> det = new HashMap<>();
                det.put("className", "Drowning");
                double confidence = 0.70 + random.nextDouble() * 0.25;
                det.put("confidence", confidence);
                det.put("bbox", Arrays.asList(0.2, 0.3, 0.6, 0.8));
                det.put("trackerId", 1);
                detections.add(det);

                config.put("counts", counts);
                config.put("detections", detections);
                config.put("timestamp", LocalDateTime.now().toString());
                camera.setExtraConfig(config);
                deviceService.updateDevice(camera);

                log.info("本地仿真探测到疑似溺水警报，设备: {}, 置信度: {}", camera.getDeviceName(), confidence);
                triggerAlertIfNeeded(camera, confidence, "Drowning", null);
            } else {
                if (random.nextDouble() < 0.3) {
                    counts.put("People", 1);
                    Map<String, Object> det = new HashMap<>();
                    det.put("className", "People");
                    det.put("confidence", 0.85);
                    det.put("bbox", Arrays.asList(0.4, 0.5, 0.8, 0.9));
                    detections.add(det);
                }
                config.put("counts", counts);
                config.put("detections", detections);
                config.put("timestamp", LocalDateTime.now().toString());
                camera.setExtraConfig(config);
                deviceService.updateDevice(camera);
            }
        }
    }

    private final Map<Long, Long> lastAlertTimeMap = new HashMap<>();

    private void triggerAlertIfNeeded(Device device, Double confidence, String className, String customSnapshotUrl) {
        if (device == null) return;

        long now = System.currentTimeMillis();
        Long lastTime = lastAlertTimeMap.get(device.getId());
        if (lastTime != null && (now - lastTime) < 10000) {
            // 10秒内同一设备不重复刷单
            return;
        }
        lastAlertTimeMap.put(device.getId(), now);

        createTaskForPythonEvent(device, confidence, className, customSnapshotUrl);
    }

    private void createTaskForPythonEvent(Device device, Double confidence, String className, String customSnapshotUrl) {
        if (device == null) return;

        AlertEvent event = new AlertEvent();
        event.setSourceDeviceId(device.getId());
        event.setWaterAreaId(device.getWaterAreaId());

        double confPercent = confidence > 1 ? confidence : confidence * 100;
        event.setConfidence(BigDecimal.valueOf(confPercent));

        String alertType = ("Drowning".equalsIgnoreCase(className) || "drowing".equalsIgnoreCase(className) || "drowning".equalsIgnoreCase(className) || "person".equalsIgnoreCase(className)) ? "DROWNING" : (className != null ? className.toUpperCase() : "DROWNING");
        event.setAlertType(alertType);
        event.setStatus("PENDING");

        // 存入新增设备打点时绑定的三维地图打点坐标及位置描述（严密校验非空与格式）
        String devLoc = device.getLocation();
        double[] devCoords = parseWktPoint(devLoc);
        if (devLoc != null && !devLoc.trim().isEmpty() && devCoords != null) {
            event.setAlertLocation(devLoc);
            event.setLongitude(devCoords[0]);
            event.setLatitude(devCoords[1]);
        } else {
            event.setAlertLocation("POINT(120.0318156 28.5093682)");
            event.setLongitude(120.0318156);
            event.setLatitude(28.5093682);
        }

        // 抓拍截图 URL (绑定带框的快照)
        if (customSnapshotUrl != null) {
            event.setSnapshotUrl(customSnapshotUrl);
        } else {
            event.setSnapshotUrl("/static/recordings/rec_" + device.getDeviceCode() + "_snapshot.jpg");
        }

        AlertEvent createdEvent = alertEventService.receiveAlert(event);

        if (createdEvent != null && createdEvent.getId() != null) {
            try {
                rescueTaskService.dispatchTask(createdEvent.getId(), null);
                log.info("AI模型/事件中心产生全新救援任务单！设备: {}, 警报事件: AE{}", device.getDeviceName(), createdEvent.getEventNo());
            } catch (Exception e) {
                log.warn("产生救援任务单警告: {}", e.getMessage());
            }
        }
    }

    private double[] parseWktPoint(String location) {
        if (location == null) return null;
        try {
            // e.g. "POINT(113.2644 23.1291)"
            String content = location.substring(location.indexOf("(") + 1, location.indexOf(")"));
            String[] parts = content.split(" ");
            return new double[] { Double.parseDouble(parts[0]), Double.parseDouble(parts[1]) };
        } catch (Exception e) {
            return null;
        }
    }
}
