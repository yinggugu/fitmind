package com.fitmind.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitmind.entity.MealRecord;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class QwenHealthLetterClient {
    private static final Logger log = LoggerFactory.getLogger(QwenHealthLetterClient.class);
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${fitmind.qwen.api-key:}") private String apiKey;
    @Value("${fitmind.qwen.base-url:}") private String baseUrl;
    @Value("${fitmind.qwen.model:qwen3.7-plus}") private String model;

    public QwenHealthLetterClient(ObjectMapper objectMapper, RestTemplateBuilder builder) {
        this.objectMapper = objectMapper;
        this.restTemplate = builder
            .setConnectTimeout(Duration.ofSeconds(12))
            .setReadTimeout(Duration.ofSeconds(45))
            .build();
    }

    public Optional<AiLetter> generate(LocalDate reportDate, LocalDate analysisDate,
                                       List<MealRecord> meals, BigDecimal calories,
                                       BigDecimal protein, BigDecimal carbohydrate,
                                       BigDecimal fat) {
        if (blank(apiKey) || blank(baseUrl) || blank(model)) return Optional.empty();
        try {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("model", model);
            request.put("temperature", 0.65);
            request.put("max_tokens", 1100);
            request.put("enable_thinking", false);
            request.put("messages", Arrays.asList(
                message("system", systemPrompt()),
                message("user", userPrompt(reportDate, analysisDate, meals, calories, protein, carbohydrate, fat))
            ));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey.trim());
            JsonNode root = restTemplate.exchange(
                trimSlash(baseUrl) + "/chat/completions",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                JsonNode.class
            ).getBody();
            String content = root == null ? null
                : root.path("choices").path(0).path("message").path("content").asText(null);
            AiLetter letter = parse(content);
            return valid(letter) ? Optional.of(letter) : Optional.empty();
        } catch (Exception exception) {
            log.warn("Qwen health-letter generation failed; using rule fallback: {}",
                exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> value = new LinkedHashMap<>();
        value.put("role", role);
        value.put("content", content);
        return value;
    }

    private String systemPrompt() {
        return "你是 FitMind 的温和健康陪伴助手。根据用户前一天的饮食估算数据写一封中文健康信。"
            + "语气成熟、自然、有陪伴感，不羞辱用户，不诊断疾病，不建议节食或绝食。"
            + "最多强调两个问题，并给出一个容易执行的行动。明确说明营养数据为估算值。"
            + "只输出一个 JSON 对象，不要 Markdown，字段必须为 summary、calorieAnalysis、nutritionAnalysis、todayFocus。"
            + "summary 写2至3句整体观察；calorieAnalysis 写2至3句热量与餐次分析；"
            + "nutritionAnalysis 写3至4句营养结构分析；todayFocus 写1至2句且只包含一个行动。";
    }

    private String userPrompt(LocalDate reportDate, LocalDate analysisDate, List<MealRecord> meals,
                              BigDecimal calories, BigDecimal protein,
                              BigDecimal carbohydrate, BigDecimal fat) {
        List<String> mealLines = new ArrayList<>();
        for (MealRecord meal : meals) {
            mealLines.add(meal.getMealType() + "：" + meal.getFoodName()
                + "（" + meal.getPortionDescription() + "，"
                + meal.getCaloriesKcal() + " kcal）");
        }
        return "展示日期：" + reportDate + "\n分析日期：" + analysisDate
            + "\n饮食记录：\n" + String.join("\n", mealLines)
            + "\n估算汇总：热量 " + calories + " kcal，蛋白质 " + protein
            + " g，碳水 " + carbohydrate + " g，脂肪 " + fat + " g。"
            + "\n请避免把估算数字写成医学结论，并让整封信比普通提醒更完整。";
    }

    private AiLetter parse(String content) throws Exception {
        if (blank(content)) return null;
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end <= start) return null;
        return objectMapper.readValue(content.substring(start, end + 1), AiLetter.class);
    }

    private boolean valid(AiLetter value) {
        return value != null && !blank(value.summary) && !blank(value.calorieAnalysis)
            && !blank(value.nutritionAnalysis) && !blank(value.todayFocus)
            && value.summary.length() <= 500 && value.calorieAnalysis.length() <= 500
            && value.nutritionAnalysis.length() <= 500 && value.todayFocus.length() <= 500;
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String trimSlash(String value) {
        return value.replaceAll("/+$", "");
    }

    @Data
    public static class AiLetter {
        private String summary;
        private String calorieAnalysis;
        private String nutritionAnalysis;
        private String todayFocus;
    }
}
