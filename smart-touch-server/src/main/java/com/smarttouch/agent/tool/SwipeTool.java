package com.smarttouch.agent.tool;

import com.smarttouch.common.DeviceCommand;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * 滑动工具
 */
@Component
public class SwipeTool implements ToolDefinition {

    @Override
    public String getName() {
        return "swipe";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params) {
        return Map.of("command", buildCommand(params));
    }

    public DeviceCommand buildCommand(Map<String, Object> params) {
        return DeviceCommand.builder()
                .commandId(UUID.randomUUID().toString().substring(0, 8))
                .action("swipe")
                .params(params)
                .build();
    }
}
