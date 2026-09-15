package com.fitmind.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class AssistantChatRequest {
    @NotBlank(message = "问题不能为空")
    @Size(max = 500, message = "问题不能超过500个字符")
    private String question;
}
