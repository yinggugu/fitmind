package com.fitmind.service;

import com.fitmind.assistant.HealthAssistantModelClient;
import com.fitmind.vo.AssistantChatVO;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HealthAssistantService {
    private static final Logger log = LoggerFactory.getLogger(HealthAssistantService.class);
    private final HealthAssistantModelClient modelClient;

    public AssistantChatVO ask(String question) {
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        long startedAt = System.nanoTime();
        MDC.put("assistantTraceId", traceId);
        try {
            String answer = modelClient.answer(question.trim());
            if (answer == null || answer.trim().isEmpty()) {
                throw new IllegalStateException("模型未返回文本");
            }
            log.info("AI_ASSISTANT traceId={} elapsedMs={} success=true", traceId, elapsedMs(startedAt));
            return new AssistantChatVO(answer.trim(), traceId, LocalDateTime.now(), false);
        } catch (Exception exception) {
            log.warn("AI_ASSISTANT traceId={} elapsedMs={} success=false errorType={} errorReason={}",
                traceId, elapsedMs(startedAt), exception.getClass().getSimpleName(), safeReason(exception));
            String message = "AI 健康助手暂时无法完成回答，请稍后重试。你的原始健康记录没有被修改。";
            return new AssistantChatVO(message, traceId, LocalDateTime.now(), true);
        } finally {
            MDC.remove("assistantTraceId");
        }
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private String safeReason(Exception exception) {
        Throwable cause = exception;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return cause.getClass().getSimpleName();
        }
        String singleLine = message.replace('\r', ' ').replace('\n', ' ').trim();
        return singleLine.length() <= 240 ? singleLine : singleLine.substring(0, 240);
    }
}
