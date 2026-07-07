package com.smarttouch.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.smarttouch.agent.memory.ContextManager;
import com.smarttouch.agent.memory.MemoryStrategy;
import com.smarttouch.agent.prompt.SystemPrompt;
import com.smarttouch.agent.tool.ActionSchema;
import com.smarttouch.agent.tool.ActionSchema.LlmAction;
import com.smarttouch.common.BusinessException;
import com.smarttouch.common.DeviceCommand;
import com.smarttouch.entity.Device;
import com.smarttouch.entity.TaskStep;
import com.smarttouch.gateway.CommandDispatcher;
import com.smarttouch.gateway.DeviceSession;
import com.smarttouch.gateway.WebSocketGateway;
import com.smarttouch.mapper.TaskStepMapper;
import com.smarttouch.service.DeviceService;
import com.smarttouch.service.SessionService;
import com.smarttouch.service.TaskService;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * SmartTouch Agent 主控
 * 核心决策循环：截图 → LLM推理 → 动作解析 → 指令下发 → 结果收集 → 循环
 */
@Slf4j
@Component
public class SmartTouchAgent {

    private final ChatLanguageModel visionChatModel;
    private final SystemPrompt systemPrompt;
    private final ContextManager contextManager;
    private final CommandDispatcher commandDispatcher;
    private final WebSocketGateway webSocketGateway;
    private final TaskService taskService;
    private final DeviceService deviceService;
    private final SessionService sessionService;
    private final TaskStepMapper taskStepMapper;

    @Value("${smart-touch.agent.max-steps}")
    private int maxSteps;

    @Value("${smart-touch.agent.step-timeout}")
    private Duration stepTimeout;

    public SmartTouchAgent(ChatLanguageModel visionChatModel, SystemPrompt systemPrompt,
                           ContextManager contextManager, CommandDispatcher commandDispatcher,
                           WebSocketGateway webSocketGateway, TaskService taskService,
                           DeviceService deviceService, SessionService sessionService,
                           TaskStepMapper taskStepMapper) {
        this.visionChatModel = visionChatModel;
        this.systemPrompt = systemPrompt;
        this.contextManager = contextManager;
        this.commandDispatcher = commandDispatcher;
        this.webSocketGateway = webSocketGateway;
        this.taskService = taskService;
        this.deviceService = deviceService;
        this.sessionService = sessionService;
        this.taskStepMapper = taskStepMapper;
    }

    /**
     * 执行任务的主循环（同步执行，由Controller异步调用）
     *
     * @param taskId   任务ID
     * @param deviceUuid 目标设备UUID
     * @param instruction 用户自然语言指令
     * @param stepCallback 每一步执行后的回调（用于SSE推送进度）
     */
    public void execute(Long taskId, String deviceUuid, String instruction,
                        Consumer<StepProgress> stepCallback) {
        log.info("Agent开始执行任务: taskId={}, deviceUuid={}, instruction={}", taskId, deviceUuid, instruction);

        // 初始化上下文
        contextManager.reset(instruction, MemoryStrategy.defaultStrategy());
        taskService.markRunning(taskId);

        // 获取设备分辨率信息
        Device device = deviceService.getByUuid(deviceUuid);
        int screenWidth = 1080, screenHeight = 2400;
        if (device != null && device.getResolution() != null && device.getResolution().contains("x")) {
            String[] parts = device.getResolution().split("x");
            screenWidth = Integer.parseInt(parts[0]);
            screenHeight = Integer.parseInt(parts[1]);
        }

        // 构建系统提示词（含设备分辨率信息）
        String systemPromptText = systemPrompt.renderWith(
                String.format("当前设备分辨率: %dx%d，坐标已归一化到1000x1000", screenWidth, screenHeight));

        String currentScreenshot = null;  // 当前截图base64
        int stepNo = 0;
        int consecutiveFails = 0;

        try {
            // ========== 首次截图 ==========
            currentScreenshot = captureScreenshot(taskId, deviceUuid, 0);
            if (currentScreenshot == null) {
                taskService.markFailed(taskId, "无法获取设备截图");
                return;
            }

            // ========== 主循环 ==========
            while (stepNo < maxSteps) {
                stepNo++;
                long stepStart = System.currentTimeMillis();

                // --- Step 1: LLM推理 ---
                String llmOutput = callVisionLLM(systemPromptText, currentScreenshot,
                        contextManager.buildTextContext());

                // 记录LLM交互日志
                sessionService.saveRecord(taskId, stepNo, "user",
                        contextManager.buildTextContext(), 0);
                sessionService.saveRecord(taskId, stepNo, "assistant", llmOutput, 0);

                // --- Step 2: 解析动作 ---
                LlmAction llmAction;
                try {
                    llmAction = ActionSchema.parse(llmOutput);
                } catch (JsonProcessingException e) {
                    log.warn("LLM输出解析失败: stepNo={}, output={}", stepNo, llmOutput);
                    // 返回给LLM让它重试
                    currentScreenshot = captureScreenshot(taskId, deviceUuid, stepNo);
                    consecutiveFails++;
                    continue;
                }

                // --- Step 3: 处理finish ---
                if ("finish".equals(llmAction.getAction())) {
                    String message = llmAction.getParams() != null
                            ? (String) llmAction.getParams().getOrDefault("message", "任务完成")
                            : "任务完成";
                    saveStepRecord(taskId, stepNo, "finish", "{}", null, "success",
                            llmAction.getReason(), (int)(System.currentTimeMillis() - stepStart));
                    taskService.markSuccess(taskId, message);
                    notifyProgress(stepCallback, stepNo, "finish", "success",
                            "任务完成: " + message, null);
                    log.info("任务执行完成: taskId={}, totalSteps={}", taskId, stepNo);
                    return;
                }

                // --- Step 4: 校验动作 ---
                String validationError = ActionSchema.validate(llmAction);
                if (validationError != null) {
                    log.warn("动作校验失败: stepNo={}, error={}", stepNo, validationError);
                    saveStepRecord(taskId, stepNo, llmAction.getAction(),
                            paramsToJson(llmAction.getParams()), null, "fail",
                            validationError, (int)(System.currentTimeMillis() - stepStart));
                    consecutiveFails++;
                    currentScreenshot = captureScreenshot(taskId, deviceUuid, stepNo);
                    continue;
                }

                // --- Step 5: 坐标归一化 ---
                Map<String, Object> normalizedParams = normalizeParams(llmAction, screenWidth, screenHeight);

                // --- Step 6: wait动作直接在服务端执行 ---
                if ("wait".equals(llmAction.getAction())) {
                    int ms = ((Number) llmAction.getParams().get("ms")).intValue();
                    Thread.sleep(Math.min(ms, 30000));
                    saveStepRecord(taskId, stepNo, "wait", paramsToJson(normalizedParams), null, "success",
                            llmAction.getReason(), (int)(System.currentTimeMillis() - stepStart));
                    notifyProgress(stepCallback, stepNo, "wait", "success",
                            "等待" + ms + "ms", currentScreenshot);
                    currentScreenshot = captureScreenshot(taskId, deviceUuid, stepNo);
                    consecutiveFails = 0;
                    continue;
                }

                // --- Step 7: 下发指令到设备 ---
                DeviceCommand command = DeviceCommand.builder()
                        .commandId(UUID.randomUUID().toString().substring(0, 8))
                        .action(llmAction.getAction())
                        .params(normalizedParams)
                        .build();

                Map<String, Object> ack;
                try {
                    ack = commandDispatcher.dispatch(deviceUuid, command);
                } catch (Exception e) {
                    log.error("指令下发失败: stepNo={}, action={}", stepNo, llmAction.getAction(), e);
                    saveStepRecord(taskId, stepNo, llmAction.getAction(), paramsToJson(normalizedParams),
                            null, "fail", "指令下发失败: " + e.getMessage(),
                            (int)(System.currentTimeMillis() - stepStart));
                    consecutiveFails++;
                    if (consecutiveFails >= 3) {
                        taskService.markFailed(taskId, "连续" + consecutiveFails + "次操作失败，任务终止");
                        return;
                    }
                    currentScreenshot = captureScreenshot(taskId, deviceUuid, stepNo);
                    continue;
                }

                // --- Step 8: 收集结果 ---
                String result = (String) ack.getOrDefault("result", "unknown");
                String newScreenshot = (String) ack.getOrDefault("screenshotBase64", null);
                int costMs = (int)(System.currentTimeMillis() - stepStart);

                // 保存步骤记录
                saveStepRecord(taskId, stepNo, llmAction.getAction(), paramsToJson(normalizedParams),
                        newScreenshot, result, llmAction.getReason(), costMs);

                // 更新上下文
                contextManager.addStep(stepNo, llmAction.getAction(), normalizedParams,
                        result, llmAction.getReason(), newScreenshot, costMs);

                // 回调通知进度
                notifyProgress(stepCallback, stepNo, llmAction.getAction(), result,
                        llmAction.getReason(), newScreenshot);

                // 更新任务进度
                taskService.updateCurrentStep(taskId, stepNo);

                // --- Step 9: 判断结果 ---
                if ("success".equals(result)) {
                    consecutiveFails = 0;
                    currentScreenshot = newScreenshot != null ? newScreenshot
                            : captureScreenshot(taskId, deviceUuid, stepNo);
                } else {
                    consecutiveFails++;
                    if (consecutiveFails >= 3) {
                        log.warn("连续{}次失败，任务终止: taskId={}", consecutiveFails, taskId);
                        taskService.markFailed(taskId, "连续" + consecutiveFails + "次操作失败");
                        return;
                    }
                    currentScreenshot = captureScreenshot(taskId, deviceUuid, stepNo);
                }
            }

            // 超过最大步数
            log.warn("任务超过最大步数限制: taskId={}, maxSteps={}", taskId, maxSteps);
            taskService.markTimeout(taskId);

        } catch (Exception e) {
            log.error("Agent执行异常: taskId={}", taskId, e);
            taskService.markFailed(taskId, "Agent异常: " + e.getMessage());
        }
    }

    // ==================== 内部方法 ====================

    /** 调用视觉LLM进行推理，返回JSON动作 */
    private String callVisionLLM(String systemPromptText, String screenshotBase64, String contextText) {
        // 构建多模态消息：系统提示词 + 上下文文本 + 当前截图
        SystemMessage systemMessage = SystemMessage.from(systemPromptText);

        UserMessage userMessage = UserMessage.from(
                TextContent.from(contextText),
                ImageContent.from(screenshotBase64, "image/png")
        );

        Response<dev.langchain4j.data.message.AiMessage> response =
                visionChatModel.generate(systemMessage, userMessage);

        return response.content().text();
    }

    /** 截取设备当前屏幕 */
    private String captureScreenshot(Long taskId, String deviceUuid, int stepNo) {
        try {
            DeviceCommand command = DeviceCommand.builder()
                    .commandId(UUID.randomUUID().toString().substring(0, 8))
                    .action("screenshot")
                    .params(Map.of())
                    .build();
            Map<String, Object> ack = commandDispatcher.dispatch(deviceUuid, command);
            String screenshot = (String) ack.getOrDefault("screenshotBase64", null);
            if (screenshot != null) {
                sessionService.saveRecord(taskId, stepNo, "tool",
                        "[截图 base64, 长度=" + screenshot.length() + "]", screenshot.length() / 1000);
            }
            return screenshot;
        } catch (Exception e) {
            log.warn("截图失败: deviceUuid={}", deviceUuid, e);
            return null;
        }
    }

    /** 坐标归一化 */
    private Map<String, Object> normalizeParams(LlmAction llmAction, int screenWidth, int screenHeight) {
        Map<String, Object> params = llmAction.getParams();
        if (params == null) return Map.of();

        return switch (llmAction.getAction()) {
            case "click" -> {
                int[] coords = ActionSchema.normalizeClick(params, screenWidth, screenHeight);
                yield Map.of("x", coords[0], "y", coords[1]);
            }
            case "swipe" -> {
                int[] coords = ActionSchema.normalizeSwipe(params, screenWidth, screenHeight);
                yield Map.of("x1", coords[0], "y1", coords[1], "x2", coords[2], "y2", coords[3]);
            }
            default -> params; // type/wait/finish 不需要坐标变换
        };
    }

    /** 保存步骤明细到数据库 */
    private void saveStepRecord(Long taskId, int stepNo, String action, String actionJson,
                                String screenshotUrl, String result, String reason, int costMs) {
        TaskStep step = TaskStep.builder()
                .taskId(taskId)
                .stepNo(stepNo)
                .action(action)
                .actionJson(actionJson)
                .screenshotUrl(screenshotUrl != null ? "data:image/png;base64,长度=" + screenshotUrl.length() : "")
                .result(result)
                .llmReason(reason)
                .costMs(costMs)
                .createTime(LocalDateTime.now())
                .build();
        taskStepMapper.insert(step);
    }

    /** 参数Map转JSON字符串 */
    private String paramsToJson(Map<String, Object> params) {
        if (params == null) return "{}";
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(params);
        } catch (Exception e) {
            return "{}";
        }
    }

    /** 通知步骤进度（SSE回调） */
    private void notifyProgress(Consumer<StepProgress> callback, int stepNo, String action,
                                String result, String reason, String screenshotBase64) {
        if (callback != null) {
            callback.accept(new StepProgress(stepNo, action, result, reason, screenshotBase64));
        }
    }

    // ==================== 进度DTO ====================

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class StepProgress {
        private int stepNo;
        private String action;
        private String result;
        private String reason;
        private String screenshotBase64;
    }
}
