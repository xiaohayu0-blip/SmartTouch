package com.smarttouch.agent.tool;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 等待工具（服务端直接sleep，无需下发到设备）
 */
@Component
public class WaitTool implements ToolDefinition {

    @Override
    public String getName() {
        return "wait";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params) throws InterruptedException {
        int ms = ((Number) params.get("ms")).intValue();
        Thread.sleep(Math.min(ms, 30000)); // 最多等30秒
        return Map.of("result", "success", "waited_ms", ms);
    }
}
