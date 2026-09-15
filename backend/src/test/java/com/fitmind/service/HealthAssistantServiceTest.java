package com.fitmind.service;

import com.fitmind.assistant.HealthAssistantModelClient;
import com.fitmind.vo.AssistantChatVO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthAssistantServiceTest {
    private final HealthAssistantModelClient client = mock(HealthAssistantModelClient.class);
    private final HealthAssistantService service = new HealthAssistantService(client);

    @Test
    void returnsModelAnswerWhenSuccessful() {
        when(client.answer("最近7天怎么样")).thenReturn("最近7条体重记录显示整体下降。");
        AssistantChatVO result = service.ask("最近7天怎么样");
        assertFalse(result.isDegraded());
        assertTrue(result.getAnswer().contains("下降"));
        assertNotNull(result.getTraceId());
    }

    @Test
    void degradesWithoutChangingDataWhenModelFails() {
        when(client.answer("分析饮食")).thenThrow(new RuntimeException("timeout"));
        AssistantChatVO result = service.ask("分析饮食");
        assertTrue(result.isDegraded());
        assertTrue(result.getAnswer().contains("没有被修改"));
    }
}
