package com.smarttouch.model;

import java.util.Map;

/**
 * 来自服务端的设备指令
 * JSON格式，通过WebSocket下发
 */
public class DeviceCommand {

    /** 指令唯一ID，回执时原样返回 */
    private String commandId;

    /** 动作类型：click/swipe/type/screenshot/wait */
    private String action;

    /** 动作参数（坐标已转为实际像素值） */
    private Map<String, Object> params;

    /** 指令时间戳 */
    private long timestamp;

    public DeviceCommand() {}

    public String getCommandId() { return commandId; }
    public void setCommandId(String commandId) { this.commandId = commandId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public Map<String, Object> getParams() { return params; }
    public void setParams(Map<String, Object> params) { this.params = params; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
