package com.hc.drown.module.device.websocket;

import com.hc.drown.module.device.entity.Device;
import com.hc.drown.module.device.service.DeviceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.io.InputStream;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
public class RtspStreamWebSocketHandler extends BinaryWebSocketHandler {

    @Autowired
    private DeviceService deviceService;

    // Stream Multiplexing maps
    private final Map<Long, Process> activeProcesses = new ConcurrentHashMap<>();
    private final Map<Long, Set<WebSocketSession>> activeSessions = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long deviceId = getDeviceId(session);
        if (deviceId == null) {
            log.warn("Invalid deviceId in WebSocket connection URI: {}", session.getUri());
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        Device device = deviceService.getDeviceById(deviceId);
        if (device == null || !"CAMERA".equalsIgnoreCase(device.getDeviceType())) {
            log.warn("Device not found or not a CAMERA: id={}", deviceId);
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        log.info("WebSocket client connected to RTSP stream for camera: {}, IP={}", device.getDeviceName(), device.getIpAddress());

        // Add to active sessions
        activeSessions.computeIfAbsent(deviceId, k -> new CopyOnWriteArraySet<>()).add(session);

        // Start transcoding process if not already running
        synchronized (activeProcesses) {
            if (!activeProcesses.containsKey(deviceId)) {
                startTranscoderProcess(deviceId, device);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Long deviceId = getDeviceId(session);
        if (deviceId != null) {
            Set<WebSocketSession> sessions = activeSessions.get(deviceId);
            if (sessions != null) {
                sessions.remove(session);
                log.info("WebSocket client disconnected from RTSP stream: deviceId={}, activeClients={}", deviceId, sessions.size());
                if (sessions.isEmpty()) {
                    activeSessions.remove(deviceId);
                    stopTranscoderProcess(deviceId);
                }
            }
        }
    }

    private Long getDeviceId(WebSocketSession session) {
        try {
            URI uri = session.getUri();
            if (uri == null) return null;
            String path = uri.getPath();
            String deviceIdStr = path.substring(path.lastIndexOf('/') + 1);
            return Long.parseLong(deviceIdStr);
        } catch (Exception e) {
            return null;
        }
    }

    private void startTranscoderProcess(Long deviceId, Device device) {
        log.info("Starting FFmpeg transcoder for camera: id={}, name={}", deviceId, device.getDeviceName());

        // Extract extra configuration parameters
        Map<String, Object> config = device.getExtraConfig();
        if (config == null) {
            config = new HashMap<>();
        }

        String rtspUrl;
        if (config.containsKey("streamUrl") && config.get("streamUrl") != null && !config.get("streamUrl").toString().trim().isEmpty()) {
            rtspUrl = config.get("streamUrl").toString().trim();
        } else {
            String username = (String) config.getOrDefault("username", "admin");
            String password = (String) config.getOrDefault("password", "");
            Object channelObj = config.getOrDefault("channelNo", 101);
            int channelNo = channelObj instanceof Number ? ((Number) channelObj).intValue() : Integer.parseInt(channelObj.toString());

            String ipAddress = device.getIpAddress();
            Integer port = device.getPort();
            if (port == null || port == 0) {
                port = 554; // RTSP Default Port
            }

            // Build Hikvision RTSP URL
            // Format: rtsp://username:password@ip:port/Streaming/Channels/101
            rtspUrl = String.format("rtsp://%s:%s@%s:%d/Streaming/Channels/%d", username, password, ipAddress, port, channelNo);
        }
        log.info("Transcoder RTSP URL: {}", rtspUrl.contains("@") ? "rtsp://******@" + rtspUrl.substring(rtspUrl.lastIndexOf('@') + 1) : rtspUrl);

        // FFmpeg command to transcode RTSP into MPEG-1 Video Stream (MPEG-TS container)
        List<String> cmd = Arrays.asList(
                "ffmpeg",
                "-rtsp_transport", "tcp",
                "-i", rtspUrl,
                "-f", "mpegts",
                "-codec:v", "mpeg1video",
                "-s", "640x360",
                "-b:v", "800k",
                "-r", "25",
                "-an",
                "-"
        );

        try {
            ProcessBuilder builder = new ProcessBuilder(cmd);
            builder.redirectErrorStream(false); // Do not merge error stream with stdout
            Process process = builder.start();
            activeProcesses.put(deviceId, process);

            // Log stderr in a separate thread to assist debugging without blocking stdout
            executorService.submit(() -> {
                try (InputStream errorStream = process.getErrorStream()) {
                    byte[] errBuffer = new byte[1024];
                    int read;
                    while ((read = errorStream.read(errBuffer)) != -1) {
                        String errMsg = new String(errBuffer, 0, read);
                        if (errMsg.contains("Error") || errMsg.contains("failed") || errMsg.contains("Server returned")) {
                            log.warn("FFmpeg [Cam {}]: {}", deviceId, errMsg.trim());
                        }
                    }
                } catch (Exception e) {
                    // Ignore stream closed exceptions
                }
            });

            // Start reading stdout and pushing to WebSocket clients
            executorService.submit(() -> {
                byte[] buffer = new byte[8192];
                try (InputStream stdout = process.getInputStream()) {
                    int bytesRead;
                    while ((bytesRead = stdout.read(buffer)) != -1) {
                        Set<WebSocketSession> sessions = activeSessions.get(deviceId);
                        if (sessions != null && !sessions.isEmpty()) {
                            BinaryMessage message = new BinaryMessage(java.nio.ByteBuffer.wrap(buffer, 0, bytesRead));
                            for (WebSocketSession s : sessions) {
                                if (s.isOpen()) {
                                    try {
                                        s.sendMessage(message);
                                    } catch (Exception e) {
                                        log.warn("Failed to send video chunk to session: {}", s.getId());
                                    }
                                }
                            }
                        } else {
                            // No sessions connected, stop transcoder process
                            log.info("No active clients for camera {}, shutting down transcoder.", deviceId);
                            stopTranscoderProcess(deviceId);
                            break;
                        }
                    }
                } catch (Exception e) {
                    log.error("Error reading FFmpeg transcoder stdout for deviceId={}: {}", deviceId, e.getMessage());
                } finally {
                    stopTranscoderProcess(deviceId);
                }
            });

        } catch (Exception e) {
            log.error("Failed to start FFmpeg transcoder process for deviceId={}: {}", deviceId, e.getMessage());
        }
    }

    private void stopTranscoderProcess(Long deviceId) {
        synchronized (activeProcesses) {
            Process p = activeProcesses.remove(deviceId);
            if (p != null) {
                log.info("Stopping FFmpeg transcoder process for camera ID: {}", deviceId);
                p.destroyForcibly();
            }
        }
    }
}
