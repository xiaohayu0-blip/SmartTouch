package com.smarttouch.agent.tool;

import com.smarttouch.common.DeviceCommand;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * 点击工具
 */
@Component
public class ClickTool implements ToolDefinition {

    @Override
    public String getName() {
        return "click";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params) {
        return Map.of("command", buildCommand(params));
    }

    /** 构建点击指令，坐标已由ActionSchema归一化为实际像素 */
    public DeviceCommand buildCommand(Map<String, Object> params) {
        return DeviceCommand.builder()
                .commandId(UUID.randomUUID().toString().substring(0, 8))
                .action("click")
                .params(params)
                .build();
    }
}
