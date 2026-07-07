package com.smarttouch.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * LangChain4j AI模型配置
 * 对接OpenAI兼容接口（DeepSeek/Qwen/智谱等）
 */
@Slf4j
@Configuration
public class AgentConfig {

    @Value("${langchain4j.openai.api-key}")
    private String apiKey;

    @Value("${langchain4j.openai.base-url}")
    private String baseUrl;

    @Value("${langchain4j.openai.model-name}")
    private String modelName;

    @Value("${langchain4j.openai.vision-model}")
    private String visionModel;

    /** 文本推理模型（任务规划用） */
    @Bean
    public ChatLanguageModel chatLanguageModel() {
        log.info("初始化LLM: baseUrl={}, model={}", baseUrl, modelName);
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(0.1)       // 低温度确保输出稳定
                .maxTokens(2048)
                .timeout(Duration.ofSeconds(60))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    /** 视觉理解模型（截图分析用） */
    @Bean
    public ChatLanguageModel visionChatModel() {
        log.info("初始化视觉LLM: baseUrl={}, model={}", baseUrl, visionModel);
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(visionModel)
                .temperature(0.1)
                .maxTokens(2048)
                .timeout(Duration.ofSeconds(90))  // 视觉模型推理较慢
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    /** 流式输出模型（SSE推送用） */
    @Bean
    public OpenAiStreamingChatModel streamingChatModel() {
        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(0.1)
                .maxTokens(2048)
                .timeout(Duration.ofSeconds(60))
                .build();
    }
}
