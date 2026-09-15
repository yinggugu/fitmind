package com.fitmind.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitmind.exception.BusinessException;
import com.fitmind.vo.ImageAnalysisVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Service
public class ImageAnalysisService {
    private static final long MAX_SIZE = 5L * 1024 * 1024;
    private static final Set<String> TYPES = new HashSet<>(Arrays.asList("image/jpeg", "image/png", "image/webp"));
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${fitmind.mimo.api-key:}") private String apiKey;
    @Value("${fitmind.mimo.base-url:}") private String baseUrl;
    @Value("${fitmind.mimo.model:}") private String model;

    public ImageAnalysisService(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    public ImageAnalysisVO analyze(MultipartFile file) {
        validate(file);
        ensureConfigured();
        Path temp = null;
        try {
            temp = Files.createTempFile("fitmind-meal-", ".upload");
            file.transferTo(temp.toFile());
            byte[] bytes = Files.readAllBytes(temp);
            return callMimo(file.getContentType(), bytes);
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new BusinessException(500, "图片处理失败，请稍后重试。");
        } finally {
            if (temp != null) try { Files.deleteIfExists(temp); } catch (IOException ignored) { }
        }
    }

    private ImageAnalysisVO callMimo(String contentType, byte[] bytes) {
        String dataUrl = "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(bytes);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_output_tokens", 2000);
        Map<String,Object> reasoning=new LinkedHashMap<>(); reasoning.put("effort","none"); body.put("reasoning",reasoning);
        body.put("input", Collections.singletonList(message(dataUrl)));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(endpoint(), HttpMethod.POST,
                new HttpEntity<>(body, headers), JsonNode.class);
            return parse(response.getBody());
        } catch (HttpStatusCodeException exception) {
            int status = exception.getRawStatusCode();
            if (status == 404) return callChatCompletions(dataUrl, headers);
            if (status == 401 || status == 403) throw new BusinessException(502, "图片识别认证失败，请检查 MiMo API Key。");
            if (status == 429) throw new BusinessException(503, "图片识别请求过多或额度不足，请稍后重试。");
            throw new BusinessException(502, "图片识别服务暂时不可用（" + status + "），请稍后重试。");
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(502, "无法连接图片识别服务，请检查网络后重试。");
        }
    }

    private ImageAnalysisVO callChatCompletions(String dataUrl, HttpHeaders headers) {
        Map<String,Object> imageUrl=new LinkedHashMap<>(); imageUrl.put("url",dataUrl);
        Map<String,Object> image=new LinkedHashMap<>(); image.put("type","image_url"); image.put("image_url",imageUrl);
        Map<String,Object> text=new LinkedHashMap<>(); text.put("type","text"); text.put("text","识别图片中的主要食物并估算整份营养。只输出JSON对象：{\"foodName\":\"食物名称\",\"portionDescription\":\"约200g\",\"caloriesKcal\":320,\"proteinG\":18,\"carbohydrateG\":42,\"fatG\":9}");
        Map<String,Object> msg=new LinkedHashMap<>(); msg.put("role","user"); msg.put("content",Arrays.asList(text,image));
        Map<String,Object> body=new LinkedHashMap<>(); body.put("model",model); body.put("messages",Collections.singletonList(msg)); body.put("max_tokens",2000); body.put("reasoning_effort","none");
        try { JsonNode root=restTemplate.exchange(baseUrl.replaceAll("/+$","")+"/chat/completions",HttpMethod.POST,new HttpEntity<>(body,headers),JsonNode.class).getBody(); return parseChat(root); }
        catch(HttpStatusCodeException e){int status=e.getRawStatusCode();if(status==401||status==403)throw new BusinessException(502,"图片识别认证失败，请检查 MiMo API Key。");if(status==429)throw new BusinessException(503,"图片识别请求过多或额度不足，请稍后重试。");throw new BusinessException(502,"图片识别服务暂时不可用（"+status+"），请稍后重试。");}
        catch(IOException e){throw new BusinessException(502,"图片识别结果格式异常，请重新识别。");}
    }

    private ImageAnalysisVO parseChat(JsonNode root) throws IOException {
        String output=root==null?null:root.path("choices").path(0).path("message").path("content").asText(null);
        return parseText(output);
    }

    private Map<String, Object> message(String dataUrl) {
        Map<String, Object> image = new LinkedHashMap<>();
        image.put("type", "input_image"); image.put("image_url", dataUrl);
        Map<String, Object> text = new LinkedHashMap<>();
        text.put("type", "input_text");
        text.put("text", "识别图片中的主要食物并估算整份营养。只输出一个JSON对象，不要Markdown：{\"foodName\":\"食物名称\",\"portionDescription\":\"约200g\",\"caloriesKcal\":320,\"proteinG\":18,\"carbohydrateG\":42,\"fatG\":9}。数值必须非负；看不清时使用保守估算，并在食物名称中说明可能的食物。此结果仅用于日常饮食记录，不是医学结论。");
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user"); message.put("content", Arrays.asList(text, image));
        return message;
    }

    private ImageAnalysisVO parse(JsonNode root) throws IOException {
        String output = root == null ? null : root.path("output_text").asText(null);
        if (output == null || output.trim().isEmpty()) {
            JsonNode items = root == null ? null : root.path("output");
            if (items != null) for (JsonNode item : items) for (JsonNode content : item.path("content")) {
                if ("output_text".equals(content.path("type").asText()) || "text".equals(content.path("type").asText())) {
                    output = content.path("text").asText();
                    if (!output.isEmpty()) break;
                }
            }
        }
        return parseText(output);
    }

    private ImageAnalysisVO parseText(String output) throws IOException {
        if (output == null || output.trim().isEmpty()) throw new BusinessException(502, "图片识别没有返回可用结果，请换一张清晰图片重试。");
        int start = output.indexOf('{'), end = output.lastIndexOf('}');
        if (start < 0 || end <= start) throw new BusinessException(502, "图片识别结果格式异常，请重新识别。");
        ImageAnalysisVO result = objectMapper.readValue(output.substring(start, end + 1), ImageAnalysisVO.class);
        validateResult(result);
        return result;
    }

    private void validateResult(ImageAnalysisVO value) {
        if (value.getFoodName() == null || value.getFoodName().trim().isEmpty()) throw new BusinessException(502, "未能识别食物，请换一张清晰图片重试。");
        value.setCaloriesKcal(nonNegative(value.getCaloriesKcal()));
        value.setProteinG(nonNegative(value.getProteinG()));
        value.setCarbohydrateG(nonNegative(value.getCarbohydrateG()));
        value.setFatG(nonNegative(value.getFatG()));
    }

    private BigDecimal nonNegative(BigDecimal value) { return value == null || value.signum() < 0 ? BigDecimal.ZERO : value; }
    private String endpoint() { return baseUrl.replaceAll("/+$", "") + "/responses"; }
    private void ensureConfigured() {
        if (blank(apiKey) || blank(baseUrl) || blank(model)) throw new BusinessException(503, "图片识别尚未配置，请手动填写食物信息。");
    }
    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException(400, "请选择图片文件。");
        if (file.getSize() > MAX_SIZE) throw new BusinessException(400, "图片大小不能超过5MB。");
        if (!TYPES.contains(file.getContentType())) throw new BusinessException(400, "仅支持 JPG、PNG 或 WEBP 图片。");
    }
}
