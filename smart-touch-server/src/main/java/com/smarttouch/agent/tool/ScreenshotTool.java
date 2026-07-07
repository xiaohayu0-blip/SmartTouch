package com.smarttouch.agent.tool;

import com.smarttouch.common.DeviceCommand;
import com.smarttouch.gateway.CommandDispatcher;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * 截图工具
 * 请求Android设备截取当前屏幕并返回base64图片
 */
@Component
public class ScreenshotTool implements ToolDefinition {

    private final CommandDispatcher dispatcher;

    public ScreenshotTool(CommandDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public String getName() {
        return "screenshot";
    }

    /**
     * 执行截图
     * @param params 无需参数（使用空Map）
     * @return 设备回执（含screenshotBase64字段）
     */
    @Override
    public Map<String, Object> execute(Map<String, Object> params) throws Exception {
        DeviceCommand command = DeviceCommand.builder()
                .commandId(UUID.randomUUID().toString().substring(0, 8))
                .action("screenshot")
                .params(Map.of())
                .build();
        // 截图需要指定目标设备，这里由调用方通过dispatch传入
        return Map.of("command", command);
    }

    /** 构建截图指令（不执行，交Agent主控分发） */
    public DeviceCommand buildCommand() {
        return DeviceCommand.builder()
                .commandId(UUID.randomUUID().toString().substring(0, 8))
                .action("screenshot")
                .params(Map.of())
                .build();
    }
}
