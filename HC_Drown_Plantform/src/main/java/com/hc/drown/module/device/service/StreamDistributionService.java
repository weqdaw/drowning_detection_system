package com.hc.drown.module.device.service;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 监控分发系统集成服务接口
 */
public interface StreamDistributionService {

    /**
     * 获取所有运行中通道的实时目标检测结果
     */
    List<StreamData> getRealtimeData();

    /**
     * 获取视频处理平台中的所有监控流通道列表
     */
    List<StreamInfo> getStreams();

    /**
     * 针对特定直播流通道，动态加载/更新规则配置（例如 AI 告警与自动录像触发阈值、ROI 区域、音频配置）
     * @param streamId 流通道ID (对应设备Code)
     * @param extraConfig 规则配置 Map
     * @return 是否同步成功
     */
    boolean updateRules(String streamId, Map<String, Object> extraConfig);

    /**
     * 针对特定直播流通道，动态加载/切换不同的AI算法模型
     * @param streamId 流通道ID (对应设备Code)
     * @param modelName 模型文件名，例如 best.pt
     * @return 是否切换成功
     */
    boolean switchModel(String streamId, String modelName);

    /**
     * 针对特定直播流通道，动态调节识别灵敏度与微调防误报策略
     * @param streamId 流通道ID (对应设备Code)
     * @param confidence 灵敏度/置信度阈值 (0.10 ~ 0.75)
     * @return 是否更新成功
     */
    boolean updateConfidence(String streamId, float confidence);

    /**
     * 从视频分析平台获取目标监测历史事件记录
     * @param limit 限制获取条数
     * @return 历史事件列表
     */
    List<Map<String, Object>> getEvents(Integer limit);

    /**
     * 获取视频录像清单
     */
    List<Map<String, Object>> getRecordings(String streamId, Integer limit);

    /**
     * 删除录像记录及文件
     */
    boolean deleteRecording(Integer id);

    /**
     * 清空所有录像记录及物理文件
     */
    boolean deleteAllRecordings();

    /**
     * 删除单条监测历史记录
     */
    boolean deleteEvent(Integer id);

    /**
     * 清空所有监测历史记录
     */
    boolean deleteAllEvents();

    @Data
    class StreamData {
        @com.fasterxml.jackson.annotation.JsonProperty("stream_id")
        private String streamId;

        private String status; // Running, Stopped, etc.

        @com.fasterxml.jackson.annotation.JsonProperty("selected_model")
        private String selectedModel;

        private String device; // cuda, cpu

        @com.fasterxml.jackson.annotation.JsonProperty("inference_fps")
        private Integer inferenceFps;

        @com.fasterxml.jackson.annotation.JsonProperty("decode_fps")
        private Integer decodeFps;

        private Map<String, Integer> counts;
        private List<Detection> detections;
        private String timestamp;
    }

    @Data
    class Detection {
        @com.fasterxml.jackson.annotation.JsonProperty("class_name")
        private String className;

        private Double confidence;
        private List<Double> bbox;

        @com.fasterxml.jackson.annotation.JsonProperty("tracker_id")
        private Integer trackerId;
    }

    @Data
    class StreamInfo {
        private String id;
        private String name;

        @com.fasterxml.jackson.annotation.JsonProperty("input_url")
        private String inputUrl;

        @com.fasterxml.jackson.annotation.JsonProperty("is_active")
        private Boolean isActive;

        private String status;

        @com.fasterxml.jackson.annotation.JsonProperty("selected_model")
        private String selectedModel;

        private Double confidence;
        private String device;

        @com.fasterxml.jackson.annotation.JsonProperty("decode_fps")
        private Integer decodeFps;

        @com.fasterxml.jackson.annotation.JsonProperty("inference_fps")
        private Integer inferenceFps;

        @com.fasterxml.jackson.annotation.JsonProperty("play_url")
        private String playUrl;
    }
}
