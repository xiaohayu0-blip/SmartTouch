package com.smarttouch.agent.tool;

import java.util.Map;

/**
 * 工具定义接口
 * 所有设备操作工具的统一抽象
 */
public interface ToolDefinition {

    /** 工具名称（对应LLM输出的action字段） */
    String getName();

    /** 执行工具，返回设备回执 */
    Map<String, Object> execute(Map<String, Object> params) throws Exception;
}
