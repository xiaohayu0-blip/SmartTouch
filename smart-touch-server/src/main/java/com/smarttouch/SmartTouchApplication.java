package com.smarttouch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SmartTouch 启动类
 * AI驱动的手机自动化操作平台后端服务
 */
@EnableAsync
@EnableScheduling
@SpringBootApplication
public class SmartTouchApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartTouchApplication.class, args);
    }
}
