package com.smarttouch.config;

import com.smarttouch.gateway.WebSocketGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置
 * 注册设备通信网关，路径：/ws/device
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final WebSocketGateway webSocketGateway;

    public WebSocketConfig(WebSocketGateway webSocketGateway) {
        this.webSocketGateway = webSocketGateway;
    }

    /**
     * 注册WebSocket处理器
     * 允许跨域（Android设备连接不受同源策略限制）
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocketGateway, "/ws/device")
                .setAllowedOrigins("*");  // 设备端连接不受限
    }
}
