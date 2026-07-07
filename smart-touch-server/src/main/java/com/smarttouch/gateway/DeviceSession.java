package com.smarttouch.gateway;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;

/**
 * 设备会话封装
 * 将WebSocket连接与设备元数据绑定
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceSession {

    /** 设备唯一标识 */
    private String deviceUuid;

    /** 设备名称/型号 */
    private String deviceName;

    /** 屏幕分辨率（宽x高） */
    private String resolution;

    /** WebSocket会话 */
    private WebSocketSession session;

    /** 设备状态：0离线 1在线 2执行中 */
    private Integer status;

    /** 最后心跳时间 */
    private LocalDateTime lastHeartbeat;

    /** 上线时间 */
    private LocalDateTime onlineTime;

    /** 当前正在执行的任务ID（无任务时为null） */
    private Long currentTaskId;
}
