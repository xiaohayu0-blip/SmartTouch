package com.smarttouch.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttouch.agent.SmartTouchAgent;
import com.smarttouch.agent.SmartTouchAgent.StepProgress;
import com.smarttouch.common.BusinessException;
import com.smarttouch.common.Result;
import com.smarttouch.entity.Device;
import com.smarttouch.entity.Task;
import com.smarttouch.entity.TaskStep;
import com.smarttouch.gateway.WebSocketGateway;
import com.smarttouch.service.DeviceService;
import com.smarttouch.service.TaskService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Agent 交互 Controller
 * 任务执行入口 + SSE实时进度推送
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final SmartTouchAgent smartTouchAgent;
    private final TaskService taskService;
    private final DeviceService deviceService;
    private final WebSocketGateway webSocketGateway;
    private final ObjectMapper objectMapper;

    public AgentController(SmartTouchAgent smartTouchAgent, TaskService taskService,
                           DeviceService deviceService, WebSocketGateway webSocketGateway,
                           ObjectMapper objectMapper) {
        this.smartTouchAgent = smartTouchAgent;
        this.taskService = taskService;
        this.deviceService = deviceService;
        this.webSocketGateway = webSocketGateway;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行任务（SSE流式推送每一步进度）
     * 前端通过EventSource连接，实时接收每一步的执行结果
     */
    @PostMapping(value = "/execute", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter execute(@RequestBody @Validated ExecuteRequest request) {
        SseEmitter emitter = new SseEmitter(10 * 60 * 1000L); // 10分钟超时

        // 校验设备在线
        Device device = deviceService.getByUuid(request.getDeviceUuid());
        if (device == null) {
            throw new BusinessException(404, "设备不存在: " + request.getDeviceUuid());
        }
        if (!webSocketGateway.isOnline(request.getDeviceUuid())) {
            throw new BusinessException(400, "设备离线，无法执行任务");
        }

        // 先创建任务
        Task task = taskService.createTask(device.getId(), request.getInstruction());
        Long taskId = task.getId();
        String deviceUuid = request.getDeviceUuid();

        // 异步执行Agent（避免阻塞Servlet线程）
        CompletableFuture.runAsync(() -> {
            try {
                smartTouchAgent.execute(taskId, deviceUuid, request.getInstruction(),
                        progress -> sendProgress(emitter, taskId, progress));
                // 任务完成，关闭SSE
                emitter.send(SseEmitter.event()
                        .name("complete")
                        .data(objectMapper.writeValueAsString(
                                Result.success(taskService.getById(taskId)))));
                emitter.complete();
            } catch (Exception e) {
                log.error("任务执行异常: taskId={}", taskId, e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(objectMapper.writeValueAsString(
                                    Result.error(500, e.getMessage()))));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        });

        // 立即返回SSE连接
        try {
            emitter.send(SseEmitter.event()
                    .name("start")
                    .data(objectMapper.writeValueAsString(
                            Result.success(Map.of("taskId", taskId, "taskNo", task.getTaskNo())))));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }

        return emitter;
    }

    /** 发送一步进度事件 */
    private void sendProgress(SseEmitter emitter, Long taskId, StepProgress progress) {
        try {
            String json = objectMapper.writeValueAsString(Map.of(
                    "taskId", taskId,
                    "stepNo", progress.getStepNo(),
                    "action", progress.getAction(),
                    "result", progress.getResult(),
                    "reason", progress.getReason()
                    // 截图base64较大，不在SSE中实时推送，前端通过API获取步骤明细
            ));
            emitter.send(SseEmitter.event()
                    .name("step")
                    .data(json));
        } catch (IOException e) {
            log.warn("SSE推送失败: taskId={}, stepNo={}", taskId, progress.getStepNo());
        }
    }

    /**
     * 查询任务步骤明细（含截图URL，用于步骤回放）
     */
    @GetMapping("/task/{taskId}/steps")
    public Result<List<TaskStep>> getSteps(@PathVariable @NotNull Long taskId) {
        return Result.success(taskService.getTaskSteps(taskId));
    }

    // ==================== 请求体 ====================

    @Data
    public static class ExecuteRequest {
        @NotBlank(message = "设备UUID不能为空")
        private String deviceUuid;
        @NotBlank(message = "任务指令不能为空")
        private String instruction;
    }
}
