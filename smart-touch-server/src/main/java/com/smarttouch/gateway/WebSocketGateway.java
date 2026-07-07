package com.smarttouch.gateway;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttouch.entity.Device;
import com.smarttouch.mapper.DeviceMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * WebSocket 网关
 * 维护与多台Android设备的长连接，负责消息路由、心跳检测
 */
@Slf4j
@Component
public class WebSocketGateway extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final CommandDispatcher commandDispatcher;
    private final DeviceMapper deviceMapper;

    @Value("${smart-touch.agent.heartbeat-interval}")
    private java.time.Duration heartbeatInterval;

    @Value("${smart-touch.agent.heartbeat-miss-threshold}")
    private int heartbeatMissThreshold;

    /** 设备ID → 设备会话的线程安全映射 */
    private final ConcurrentHashMap<String, DeviceSession> deviceSessions = new ConcurrentHashMap<>();

    public WebSocketGateway(ObjectMapper objectMapper, CommandDispatcher commandDispatcher,
                            DeviceMapper deviceMapper) {
        this.objectMapper = objectMapper;
        this.commandDispatcher = commandDispatcher;
        this.deviceMapper = deviceMapper;
    }

    // ==================== 连接生命周期 ====================

    /** 连接建立：从URL参数提取deviceUuid完成鉴权注册 */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String deviceUuid = extractDeviceUuid(session);
        if (deviceUuid == null || deviceUuid.isEmpty()) {
            log.warn("WebSocket连接缺少deviceUuid参数，拒绝连接: {}", session.getId());
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        // 如果设备已在线，先关闭旧连接（避免同一设备多个连接）
        DeviceSession oldSession = deviceSessions.get(deviceUuid);
        if (oldSession != null && oldSession.getSession().isOpen()) {
            log.info("设备已有旧连接，关闭旧连接: deviceUuid={}", deviceUuid);
            oldSession.getSession().close(CloseStatus.NORMAL);
        }

        // 创建设备会话
        DeviceSession deviceSession = DeviceSession.builder()
                .deviceUuid(deviceUuid)
                .session(session)
                .status(Device.STATUS_ONLINE)
                .lastHeartbeat(LocalDateTime.now())
                .onlineTime(LocalDateTime.now())
                .build();
        deviceSessions.put(deviceUuid, deviceSession);

        // 更新数据库中的设备状态
        Device device = deviceMapper.selectByDeviceUuid(deviceUuid);
        if (device != null) {
            device.setStatus(Device.STATUS_ONLINE);
            device.setLastOnline(LocalDateTime.now());
            deviceMapper.updateById(device);
        }

        log.info("设备上线: deviceUuid={}, sessionId={}, 当前在线设备数={}",
                deviceUuid, session.getId(), deviceSessions.size());
    }

    /** 连接关闭：标记设备离线 */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String deviceUuid = findDeviceUuidBySession(session);
        if (deviceUuid != null) {
            deviceSessions.remove(deviceUuid);

            // 更新数据库状态
            Device device = deviceMapper.selectByDeviceUuid(deviceUuid);
            if (device != null) {
                device.setStatus(Device.STATUS_OFFLINE);
                deviceMapper.updateById(device);
            }

            log.info("设备离线: deviceUuid={}, closeStatus={}, 当前在线设备数={}",
                    deviceUuid, status, deviceSessions.size());
        }
    }

    // ==================== 消息处理 ====================

    /** 接收设备上行消息：心跳 / 指令回执 */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        Map<String, Object> msgMap = objectMapper.readValue(payload,
                new TypeReference<Map<String, Object>>() {});

        String type = (String) msgMap.getOrDefault("type", "");
        String deviceUuid = (String) msgMap.get("deviceUuid");

        if ("heartbeat".equals(type)) {
            handleHeartbeat(deviceUuid, msgMap);
        } else if ("command_ack".equals(type)) {
            commandDispatcher.handleAck(deviceUuid, msgMap);
        } else {
            log.warn("未知消息类型: type={}, deviceUuid={}", type, deviceUuid);
        }
    }

    /** 处理心跳：更新设备最后心跳时间 */
    private void handleHeartbeat(String deviceUuid, Map<String, Object> msgMap) {
        DeviceSession session = deviceSessions.get(deviceUuid);
        if (session != null) {
            session.setLastHeartbeat(LocalDateTime.now());
            // 同步设备信息（名称、分辨率）
            if (msgMap.containsKey("deviceName")) {
                session.setDeviceName((String) msgMap.get("deviceName"));
            }
            if (msgMap.containsKey("resolution")) {
                session.setResolution((String) msgMap.get("resolution"));
            }
        }
    }

    // ==================== 指令下发 ====================

    /** 向指定设备发送指令 */
    public void sendCommand(String deviceUuid, String commandJson) {
        DeviceSession session = deviceSessions.get(deviceUuid);
        if (session == null || !session.getSession().isOpen()) {
            log.warn("设备不在线，无法下发指令: deviceUuid={}", deviceUuid);
            throw new RuntimeException("设备不在线: " + deviceUuid);
        }
        try {
            session.getSession().sendMessage(new TextMessage(commandJson));
        } catch (Exception e) {
            log.error("指令下发失败: deviceUuid={}", deviceUuid, e);
            throw new RuntimeException("指令下发失败", e);
        }
    }

    // ==================== 心跳巡检 ====================

    /** 定时检查心跳，标记超时离线设备（每30秒执行一次） */
    @Scheduled(fixedRate = 30000)
    public void checkHeartbeat() {
        LocalDateTime now = LocalDateTime.now();
        List<String> timeoutDevices = deviceSessions.entrySet().stream()
                .filter(entry -> {
                    long secondsSinceLastBeat =
                            java.time.Duration.between(entry.getValue().getLastHeartbeat(), now).getSeconds();
                    long thresholdSeconds = heartbeatInterval.getSeconds() * heartbeatMissThreshold;
                    return secondsSinceLastBeat > thresholdSeconds;
                })
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        for (String deviceUuid : timeoutDevices) {
            DeviceSession session = deviceSessions.remove(deviceUuid);
            try {
                session.getSession().close(CloseStatus.SESSION_NOT_RELIABLE);
            } catch (Exception ignored) {}
            log.warn("设备心跳超时，强制离线: deviceUuid={}", deviceUuid);
        }
    }

    // ==================== 查询方法（供其他模块调用） ====================

    /** 获取设备会话 */
    public DeviceSession getSession(String deviceUuid) {
        return deviceSessions.get(deviceUuid);
    }

    /** 设备是否在线 */
    public boolean isOnline(String deviceUuid) {
        DeviceSession session = deviceSessions.get(deviceUuid);
        return session != null && session.getSession().isOpen();
    }

    /** 获取所有在线设备UUID列表 */
    public List<String> getOnlineDeviceUuids() {
        return deviceSessions.values().stream()
                .filter(s -> s.getSession().isOpen())
                .map(DeviceSession::getDeviceUuid)
                .collect(Collectors.toList());
    }

    /** 获取在线设备数量 */
    public int getOnlineCount() {
        return (int) deviceSessions.values().stream()
                .filter(s -> s.getSession().isOpen())
                .count();
    }

    // ==================== 内部工具方法 ====================

    /** 从WebSocket连接URL中提取deviceUuid参数（/ws/device?deviceUuid=xxx） */
    private String extractDeviceUuid(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null || uri.getQuery() == null) return null;
        for (String param : uri.getQuery().split("&")) {
            String[] kv = param.split("=", 2);
            if ("deviceUuid".equals(kv[0]) && kv.length > 1) {
                return kv[1];
            }
        }
        return null;
    }

    /** 根据WebSocketSession反查deviceUuid */
    private String findDeviceUuidBySession(WebSocketSession session) {
        for (Map.Entry<String, DeviceSession> entry : deviceSessions.entrySet()) {
            if (entry.getValue().getSession().getId().equals(session.getId())) {
                return entry.getKey();
            }
        }
        return null;
    }
}
