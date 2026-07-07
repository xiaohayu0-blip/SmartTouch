package com.smarttouch.agent.prompt;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 系统提示词
 * 从 prompts/system-prompt.txt 加载，支持动态追加工具列表
 */
@Slf4j
@Component
public class SystemPrompt {

    @Getter
    private String content;

    @PostConstruct
    public void load() {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/system-prompt.txt");
            this.content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            log.info("系统提示词加载成功，长度: {} 字符", content.length());
        } catch (Exception e) {
            log.error("系统提示词加载失败", e);
            this.content = "你是一个手机操作助手。";
        }
    }

    /** 追加额外的约束文本（如设备分辨率信息） */
    public String renderWith(String extraInfo) {
        if (extraInfo == null || extraInfo.isEmpty()) {
            return content;
        }
        return content + "\n\n【当前设备信息】\n" + extraInfo;
    }
}
