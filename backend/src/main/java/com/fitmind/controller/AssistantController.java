package com.fitmind.controller;

import com.fitmind.common.Result;
import com.fitmind.dto.AssistantChatRequest;
import com.fitmind.service.HealthAssistantService;
import com.fitmind.vo.AssistantChatVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/assistant")
@RequiredArgsConstructor
public class AssistantController {
    private final HealthAssistantService service;

    @PostMapping("/chat")
    public Result<AssistantChatVO> chat(@Valid @RequestBody AssistantChatRequest request) {
        return Result.ok(service.ask(request.getQuestion()));
    }

    @GetMapping("/suggestions")
    public Result<List<String>> suggestions() {
        return Result.ok(Arrays.asList(
            "分析最近30天体重变化",
            "分析最近7天饮食",
            "哪一天热量最高",
            "总结我最近的健康数据"
        ));
    }
}
