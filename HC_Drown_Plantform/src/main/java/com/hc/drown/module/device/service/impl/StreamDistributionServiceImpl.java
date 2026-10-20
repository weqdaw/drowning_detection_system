package com.hc.drown.module.device.service.impl;

import com.hc.drown.module.device.service.StreamDistributionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * 监控分发系统集成服务实现类
 */
@Slf4j
@Service
public class StreamDistributionServiceImpl implements StreamDistributionService {

    private final RestTemplate restTemplate;

    @Value("${hc.drown.distribution.url:http://127.0.0.1:8000}")
    private String distributionUrl;

    @Value("${hc.drown.distribution.token}")
    private String distributionToken;

    // Cache to keep track of current models for streams in mock/fallback mode
    private final Map<String, String> mockSelectedModels = new HashMap<>();

    // Throttle log spam when server is down
    private long lastLogTime = 0;

    public StreamDistributionServiceImpl() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(3000);
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public List<StreamData> getRealtimeData() {
        String url = distributionUrl + "/api/realtime/data";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + distributionToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<StreamData[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    StreamData[].class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return Arrays.asList(response.getBody());
            }
        } catch (Exception e) {
            long now = System.currentTimeMillis();
            if (now - lastLogTime > 30000) { // Log every 30 seconds
                log.warn("无法连接监控分发系统 ({})，将启用本地模拟数据。错误: {}", url, e.getMessage());
                lastLogTime = now;
            }
        }

        // Return empty or wait, since scheduler can generate simulation if we return an empty list or handle it there
        return Collections.emptyList();
    }

    @Override
    public boolean updateRules(String streamId, Map<String, Object> extraConfig) {
        String url = distributionUrl + "/api/streams/" + streamId + "/rules";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + distributionToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("rules_config", extraConfig != null ? extraConfig : new HashMap<>());

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("监控分发平台热加载通道 [{}] 阈值与分析规则成功", streamId);
                return true;
            }
        } catch (Exception e) {
            log.warn("调用监控分发系统规则热加载接口异常, url: {}, 错误: {}", url, e.getMessage());
        }
        return false;
    }

    @Override
    public boolean switchModel(String streamId, String modelName) {
        String url = distributionUrl + "/api/streams/" + streamId + "/model";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + distributionToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> body = new HashMap<>();
            body.put("model_name", modelName);

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("监控分发平台切换流 [{}] AI模型为 [{}] 成功", streamId, modelName);
                mockSelectedModels.put(streamId, modelName);
                return true;
            }
        } catch (Exception e) {
            log.error("调用监控分发系统模型热加载接口异常, url: {}, 错误: {}", url, e.getMessage());
        }

        // Local mode fallback
        log.info("本地模拟：成功将流 [{}] 的AI模型切换为 [{}]", streamId, modelName);
        mockSelectedModels.put(streamId, modelName);
        return true;
    }

    @Override
    public boolean updateConfidence(String streamId, float confidence) {
        String url = distributionUrl + "/api/streams/" + streamId + "/confidence";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + distributionToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("confidence", confidence);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("监控分发平台热重载通道 [{}] 识别置信度与粒度为 [{}] 成功", streamId, confidence);
                return true;
            }
        } catch (Exception e) {
            log.warn("调用监控分发系统置信度热加载接口异常, url: {}, 错误: {}", url, e.getMessage());
        }
        return false;
    }

    @Override
    public List<StreamInfo> getStreams() {
        String url = distributionUrl + "/api/streams";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + distributionToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<StreamInfo[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    StreamInfo[].class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return Arrays.asList(response.getBody());
            }
        } catch (Exception e) {
            long now = System.currentTimeMillis();
            if (now - lastLogTime > 30000) { // Log every 30 seconds
                log.warn("无法从监控分发系统获取所有通道列表 ({})。错误: {}", url, e.getMessage());
                lastLogTime = now;
            }
        }
        return null;
    }

    @Override
    public List<Map<String, Object>> getEvents(Integer limit) {
        String url = distributionUrl + "/api/events?limit=" + limit;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + distributionToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<List> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    List.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return (List<Map<String, Object>>) response.getBody();
            }
        } catch (Exception e) {
            log.error("调用监控分发系统获取历史监测事件接口异常, url: {}, 错误: {}", url, e.getMessage());
        }
        return Collections.emptyList();
    }

    @Override
    public List<Map<String, Object>> getRecordings(String streamId, Integer limit) {
        String url = distributionUrl + "/api/recordings?limit=" + limit + (streamId != null ? "&stream_id=" + streamId : "");
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + distributionToken);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<List> response = restTemplate.exchange(url, HttpMethod.GET, entity, List.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return (List<Map<String, Object>>) response.getBody();
            }
        } catch (Exception e) {
            log.error("获取视频分析平台录像列表异常: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    @Override
    public boolean deleteRecording(Integer id) {
        String url = distributionUrl + "/api/recordings/" + id;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + distributionToken);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.DELETE, entity, Map.class);
            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            log.error("删除视频分析平台录像记录异常: {}", e.getMessage());
        }
        return false;
    }

    @Override
    public boolean deleteAllRecordings() {
        String url = distributionUrl + "/api/recordings";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + distributionToken);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.DELETE, entity, Map.class);
            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            log.error("清空视频分析平台录像记录及物理文件异常: {}", e.getMessage());
        }
        return false;
    }

    @Override
    public boolean deleteEvent(Integer id) {
        String url = distributionUrl + "/api/events/" + id;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + distributionToken);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.DELETE, entity, Map.class);
            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            log.error("删除视频分析平台监测历史记录异常: {}", e.getMessage());
        }
        return false;
    }

    @Override
    public boolean deleteAllEvents() {
        String url = distributionUrl + "/api/events";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + distributionToken);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.DELETE, entity, Map.class);
            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            log.error("清空视频分析平台监测历史记录异常: {}", e.getMessage());
        }
        return false;
    }
}
