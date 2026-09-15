package com.fitmind.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class AssistantChatVO {
    private String answer;
    private String traceId;
    private LocalDateTime generatedAt;
    private boolean degraded;
}
