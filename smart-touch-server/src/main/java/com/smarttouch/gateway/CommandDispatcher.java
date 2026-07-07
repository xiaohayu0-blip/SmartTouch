package com.smarttouch.gateway;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttouch.common.DeviceCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 指令分发器
 * 向设备下发指令并异步等待回执，支持超时重试
 */
@Slf4j
@Component
public class CommandDispatcher {

    private final WebSocketGateway gateway;
    private final ObjectMapper objectMapper;

    /** 单步指令超时时间 */
    private final Duration stepTimeout;

    /** 等待回执的Future映射：commandId → CompletableFuture */
    private final ConcurrentHashMap<String, CompletableFuture<Map<String, Object>>> pendingCommands = new ConcurrentHashMap<>();

    public CommandDispatcher(WebSocketGateway gateway, ObjectMapper objectMapper,
                             @Value("${smart-touch.agent.step-timeout}") Duration stepTimeout) {
        this.gateway = gateway;
        this.objectMapper = objectMapper;
        this.stepTimeout = stepTimeout;
    }

    /**
     * 下发指令并等待设备回执（同步阻塞）
     *
     * @param deviceUuid 目标设备
     * @param command 指令对象
     * @return 设备回执数据（含截图base64+执行结果）
     */
    public Map<String, Object> dispatch(String deviceUuid, DeviceCommand command) throws Exception {
        String commandId = command.getCommandId();
        if (commandId == null || commandId.isEmpty()) {
            commandId = UUID.randomUUID().toString().substring(0, 8);
            command.setCommandId(commandId);
        }
        command.setTimestamp(System.currentTimeMillis());

        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        pendingCommands.put(commandId, future);

        try {
            // 下发指令到设备
            String commandJson = objectMapper.writeValueAsString(command);
            gateway.sendCommand(deviceUuid, commandJson);
            log.info("指令已下发: deviceUuid={}, commandId={}, action={}",
                    deviceUuid, commandId, command.getAction());

            // 阻塞等待回执（超时后第一次重试）
            try {
                return future.get(stepTimeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                log.warn("指令超时，重试1次: deviceUuid={}, commandId={}", deviceUuid, commandId);
                // 重试：重新下发同一指令
                pendingCommands.put(commandId, new CompletableFuture<>());
                gateway.sendCommand(deviceUuid, commandJson);

                try {
                    return pendingCommands.get(commandId)
                            .get(stepTimeout.toMillis(), TimeUnit.MILLISECONDS);
                } catch (TimeoutException e2) {
                    log.error("指令重试后仍超时: deviceUuid={}, commandId={}", deviceUuid, commandId);
                    Map<String, Object> timeoutResult = Map.of(
                            "result", "timeout",
                            "commandId", commandId,
                            "error", "指令超时，重试后仍无响应"
                    );
                    return timeoutResult;
                }
            }
        } finally {
            pendingCommands.remove(commandId);
        }
    }

    /**
     * 处理设备上行回执
     * 根据commandId匹配对应的Future并完成
     */
    public void handleAck(String deviceUuid, Map<String, Object> ackData) {
        String commandId = (String) ackData.get("commandId");
        if (commandId == null) {
            log.warn("回执缺少commandId: deviceUuid={}", deviceUuid);
            return;
        }

        CompletableFuture<Map<String, Object>> future = pendingCommands.get(commandId);
        if (future != null && !future.isDone()) {
            future.complete(ackData);
            log.info("收到设备回执: deviceUuid={}, commandId={}, result={}",
                    deviceUuid, commandId, ackData.get("result"));
        } else {
            log.debug("回执无对应等待者（可能已超时）: commandId={}", commandId);
        }
    }

    /** 获取当前等待中的指令数量（监控用） */
    public int getPendingCount() {
        return pendingCommands.size();
    }
}
