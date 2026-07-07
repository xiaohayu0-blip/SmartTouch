package com.smarttouch.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 下发给Android设备的指令DTO
 * 服务端通过WebSocket下发，设备解析后执行
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceCommand {

    /** 指令唯一ID（UUID），用于回执匹配 */
    private String commandId;

    /** 动作类型：click/swipe/type/screenshot/wait */
    private String action;

    /** 动作参数（坐标、文本等） */
    private Map<String, Object> params;

    /** 指令发出时间戳（毫秒） */
    private long timestamp;
}
