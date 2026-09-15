package com.fitmind.assistant;

import com.fitmind.exception.BusinessException;
import com.fitmind.tool.HealthDataTools;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
public class LangChain4jHealthAssistantClient implements HealthAssistantModelClient {
    private final HealthDataTools healthDataTools;

    @Value("${fitmind.qwen.api-key:}")
    private String qwenApiKey;
    @Value("${fitmind.qwen.base-url:}")
    private String qwenBaseUrl;
    @Value("${fitmind.qwen.model:qwen3.7-plus}")
    private String qwenModelName;
    @Value("${fitmind.mimo.api-key:}")
    private String mimoApiKey;
    @Value("${fitmind.mimo.base-url:}")
    private String mimoBaseUrl;
    @Value("${fitmind.mimo.model:}")
    private String mimoModelName;
    @Value("${fitmind.assistant.timeout-seconds:45}")
    private long timeoutSeconds;

    private volatile ChatLanguageModel model;

    @Override
    public String answer(String question) {
        ModelConfig config = resolveConfig();
        HealthAssistantAgent agent = AiServices.builder(HealthAssistantAgent.class)
            .chatLanguageModel(model(config))
            .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
            .systemMessageProvider(memoryId -> systemPrompt())
            .tools(healthDataTools)
            .build();
        return agent.chat(question);
    }

    private ChatLanguageModel model(ModelConfig config) {
        ChatLanguageModel current = model;
        if (current == null) {
            synchronized (this) {
                current = model;
                if (current == null) {
                    current = OpenAiChatModel.builder()
                        .baseUrl(trimSlash(config.baseUrl))
                        .apiKey(config.apiKey.trim())
                        .modelName(config.modelName.trim())
                        .temperature(0.2)
                        .maxTokens(1200)
                        .timeout(Duration.ofSeconds(Math.max(5, timeoutSeconds)))
                        .maxRetries(1)
                        .logRequests(false)
                        .logResponses(false)
                        .build();
                    model = current;
                    log.info("AI_ASSISTANT_MODEL provider={} model={}", config.provider, config.modelName);
                }
            }
        }
        return current;
    }

    private String systemPrompt() {
        return "你是 FitMind AI 健康助手，今天是 " + LocalDate.now() + "。"
            + "回答与该用户体重、饮食、身体维度和目标有关的问题时，必须先调用已提供的只读工具。"
            + "所有日期、记录数、体重变化、平均值、热量与营养数字只能引用工具返回的数据，禁止自行计算、补全或猜测。"
            + "工具返回 missingData=true、status=DATA_INSUFFICIENT 或数值为 null 时，要明确说明数据不足。"
            + "不得声称修改、删除或新增了数据，不得生成或执行 SQL。"
            + "回答使用简洁自然的中文，先说结论，再列出关键数据；营养数据是估算值，不作疾病诊断或医学结论。";
    }

    private ModelConfig resolveConfig() {
        if (!blank(qwenApiKey) && !blank(qwenBaseUrl) && !blank(qwenModelName)) {
            return new ModelConfig("qwen", qwenApiKey, qwenBaseUrl, qwenModelName);
        }
        if (!blank(mimoApiKey) && !blank(mimoBaseUrl) && !blank(mimoModelName)) {
            return new ModelConfig("mimo", mimoApiKey, mimoBaseUrl, mimoModelName);
        }
        throw new BusinessException(503, "AI 健康助手尚未配置，请设置文本模型 API 配置。");
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String trimSlash(String value) {
        return value.trim().replaceAll("/+$", "");
    }

    private static class ModelConfig {
        private final String provider;
        private final String apiKey;
        private final String baseUrl;
        private final String modelName;

        private ModelConfig(String provider, String apiKey, String baseUrl, String modelName) {
            this.provider = provider;
            this.apiKey = apiKey;
            this.baseUrl = baseUrl;
            this.modelName = modelName;
        }
    }
}
