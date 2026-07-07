package com.smarttouch.agent.tool;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 动作定义
 * LLM输出的结构化动作，经校验后映射为DeviceCommand
 */
public class ActionSchema {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /** LLM输出的原始动作JSON的Java映射 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LlmAction {
        /** 动作名：click/swipe/type/wait/finish */
        private String action;
        /** 动作参数（坐标、文本等） */
        private Map<String, Object> params;
        /** LLM决策理由 */
        private String reason;
    }

    /** 解析LLM输出为动作对象 */
    public static LlmAction parse(String llmOutput) throws JsonProcessingException {
        // LLM可能输出带markdown代码块包裹的JSON，做清洗
        String json = llmOutput.trim();
        if (json.startsWith("```")) {
            json = json.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
        }
        return objectMapper.readValue(json, LlmAction.class);
    }

    /** 校验动作合法性 */
    public static String validate(LlmAction action) {
        if (action.getAction() == null || action.getAction().isEmpty()) {
            return "action字段不能为空";
        }
        return switch (action.getAction()) {
            case "click" -> validateClick(action.getParams());
            case "swipe" -> validateSwipe(action.getParams());
            case "type" -> validateType(action.getParams());
            case "wait" -> validateWait(action.getParams());
            case "finish" -> null; // finish始终合法
            default -> "未知动作类型: " + action.getAction();
        };
    }

    /** 校验并归一化click坐标（0-1000 → 设备实际像素） */
    public static int[] normalizeClick(Map<String, Object> params, int screenWidth, int screenHeight) {
        int x = ((Number) params.get("x")).intValue();
        int y = ((Number) params.get("y")).intValue();
        // 归一化坐标 → 实际像素
        int px = x * screenWidth / 1000;
        int py = y * screenHeight / 1000;
        // 边界检查
        px = Math.max(0, Math.min(px, screenWidth - 1));
        py = Math.max(0, Math.min(py, screenHeight - 1));
        return new int[]{px, py};
    }

    /** 校验并归一化swipe坐标 */
    public static int[] normalizeSwipe(Map<String, Object> params, int screenWidth, int screenHeight) {
        int x1 = ((Number) params.get("x1")).intValue();
        int y1 = ((Number) params.get("y1")).intValue();
        int x2 = ((Number) params.get("x2")).intValue();
        int y2 = ((Number) params.get("y2")).intValue();
        return new int[]{
                x1 * screenWidth / 1000, y1 * screenHeight / 1000,
                x2 * screenWidth / 1000, y2 * screenHeight / 1000
        };
    }

    private static String validateClick(Map<String, Object> params) {
        if (params == null || !params.containsKey("x") || !params.containsKey("y")) {
            return "click动作缺少x/y参数";
        }
        int x = ((Number) params.get("x")).intValue();
        int y = ((Number) params.get("y")).intValue();
        if (x < 0 || x > 1000 || y < 0 || y > 1000) {
            return "坐标超出范围: (" + x + "," + y + ")，应在0-1000之间";
        }
        return null;
    }

    private static String validateSwipe(Map<String, Object> params) {
        if (params == null || !params.containsKey("x1") || !params.containsKey("y1")
                || !params.containsKey("x2") || !params.containsKey("y2")) {
            return "swipe动作缺少x1/y1/x2/y2参数";
        }
        return null;
    }

    private static String validateType(Map<String, Object> params) {
        if (params == null || !params.containsKey("text")) {
            return "type动作缺少text参数";
        }
        return null;
    }

    private static String validateWait(Map<String, Object> params) {
        if (params == null || !params.containsKey("ms")) {
            return "wait动作缺少ms参数";
        }
        int ms = ((Number) params.get("ms")).intValue();
        if (ms < 0 || ms > 30000) {
            return "wait时间应在0-30000ms之间";
        }
        return null;
    }
}
