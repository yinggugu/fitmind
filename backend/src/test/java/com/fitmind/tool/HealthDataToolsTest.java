package com.fitmind.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitmind.entity.BodyMeasurement;
import com.fitmind.entity.MealRecord;
import com.fitmind.entity.WeightRecord;
import com.fitmind.enums.MealType;
import com.fitmind.exception.BusinessException;
import com.fitmind.service.BodyMeasurementService;
import com.fitmind.service.DashboardService;
import com.fitmind.service.MealService;
import com.fitmind.service.ProfileService;
import com.fitmind.service.WeightService;
import com.fitmind.vo.NutritionStatisticsVO;
import dev.langchain4j.agent.tool.Tool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthDataToolsTest {
    private final DashboardService dashboard = mock(DashboardService.class);
    private final WeightService weights = mock(WeightService.class);
    private final MealService meals = mock(MealService.class);
    private final BodyMeasurementService body = mock(BodyMeasurementService.class);
    private final ProfileService profile = mock(ProfileService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private HealthDataTools tools;

    @BeforeEach
    void setUp() {
        tools = new HealthDataTools(dashboard, weights, meals, body, profile, objectMapper);
    }

    @Test
    void weightChangeIsCalculatedByJava() throws Exception {
        when(weights.listBetween(any(), any())).thenReturn(Arrays.asList(
            weight("2026-08-06", "56.95"), weight("2026-08-12", "56.80")));
        JsonNode root = objectMapper.readTree(tools.calculateWeightChange("2026-08-06", "2026-08-12"));
        assertEquals("-0.15", root.path("data").path("changeKg").asText());
        assertEquals(2, root.path("recordCount").asInt());
        assertTrue(root.path("missingData").asBoolean());
    }

    @Test
    void nutritionSummaryReportsMissingDates() throws Exception {
        MealRecord latest = meal("2026-08-17", "DINNER", "500");
        when(meals.latest()).thenReturn(latest);
        when(meals.listBetween(any(), any())).thenReturn(Collections.singletonList(latest));
        NutritionStatisticsVO statistics = new NutritionStatisticsVO();
        statistics.setTotalCaloriesKcal(new BigDecimal("500"));
        when(meals.nutritionBetween(any(), any())).thenReturn(statistics);
        JsonNode root = objectMapper.readTree(tools.getRecentNutritionSummary(7));
        assertTrue(root.path("missingData").asBoolean());
        assertEquals(6, root.path("missingDates").size());
        assertEquals("500", root.path("data").path("statistics").path("totalCaloriesKcal").asText());
    }

    @Test
    void latestBodyReturnsDataInsufficientInsteadOfInventingValues() throws Exception {
        when(body.latest()).thenThrow(BusinessException.notFound("尚无身体维度记录"));
        JsonNode root = objectMapper.readTree(tools.getLatestBodyMeasurements());
        assertEquals("DATA_INSUFFICIENT", root.path("status").asText());
        assertTrue(root.path("missingData").asBoolean());
    }

    @Test
    void onlyNineReadOnlyToolsAreExposed() {
        long count = Arrays.stream(HealthDataTools.class.getDeclaredMethods())
            .filter(method -> method.isAnnotationPresent(Tool.class))
            .peek(method -> assertFalse(method.getName().matches("(?i).*(create|update|delete|save|insert).*")))
            .count();
        assertEquals(9, count);
    }

    private static WeightRecord weight(String date, String kilograms) {
        WeightRecord record = new WeightRecord();
        record.setRecordDate(LocalDate.parse(date));
        record.setWeightKg(new BigDecimal(kilograms));
        return record;
    }

    private static MealRecord meal(String date, String type, String calories) {
        MealRecord record = new MealRecord();
        record.setRecordDate(LocalDate.parse(date));
        record.setMealType(MealType.valueOf(type));
        record.setFoodName("测试食物");
        record.setCaloriesKcal(new BigDecimal(calories));
        record.setProteinG(BigDecimal.TEN);
        record.setCarbohydrateG(BigDecimal.TEN);
        record.setFatG(BigDecimal.ONE);
        record.setEstimated(true);
        return record;
    }
}
