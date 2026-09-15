package com.fitmind.service;

import com.fitmind.entity.DailyHealthLetter;
import com.fitmind.entity.MealRecord;
import com.fitmind.enums.GenerationType;
import com.fitmind.enums.MealType;
import com.fitmind.mapper.DailyHealthLetterMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HealthLetterServiceTest {
    private final DailyHealthLetterMapper mapper = mock(DailyHealthLetterMapper.class);
    private final MealService meals = mock(MealService.class);
    private final QwenHealthLetterClient qwen = mock(QwenHealthLetterClient.class);
    private final HealthLetterService service = new HealthLetterService(mapper, meals, qwen);

    @BeforeEach
    void init() {
        ReflectionTestUtils.setField(service, "calorieLow", new BigDecimal("1200"));
        ReflectionTestUtils.setField(service, "proteinMin", new BigDecimal("60"));
        ReflectionTestUtils.setField(service, "carbMin", new BigDecimal("120"));
        ReflectionTestUtils.setField(service, "fatMax", new BigDecimal("70"));
        when(mapper.insert(any())).thenReturn(1);
        when(qwen.generate(any(), any(), anyList(), any(), any(), any(), any()))
            .thenReturn(Optional.empty());
    }

    @Test
    void normalGenerationUsesRuleFallback() {
        when(meals.byDate(any())).thenReturn(Arrays.asList(
            meal(MealType.BREAKFAST, "700", "35"), meal(MealType.LUNCH, "700", "35")));
        assertEquals(GenerationType.RULE,
            service.generate(LocalDate.now(), LocalDate.now().minusDays(1)).getGenerationType());
    }

    @Test
    void aiGenerationReplacesRuleCopy() {
        when(meals.byDate(any())).thenReturn(Collections.singletonList(
            meal(MealType.LUNCH, "1300", "70")));
        QwenHealthLetterClient.AiLetter ai = new QwenHealthLetterClient.AiLetter();
        ai.setSummary("AI整体回顾");
        ai.setCalorieAnalysis("AI热量分析");
        ai.setNutritionAnalysis("AI营养分析");
        ai.setTodayFocus("AI今日行动");
        when(qwen.generate(any(), any(), anyList(), any(), any(), any(), any()))
            .thenReturn(Optional.of(ai));
        DailyHealthLetter result = service.generateToday();
        assertEquals(GenerationType.AI, result.getGenerationType());
        assertEquals("AI营养分析", result.getNutritionAnalysis());
    }

    @Test
    void idempotent() {
        DailyHealthLetter existing = new DailyHealthLetter();
        when(mapper.selectOne(any())).thenReturn(existing);
        assertSame(existing, service.generateToday());
        verify(mapper, never()).insert(any());
    }

    @Test
    void noData() {
        when(meals.byDate(any())).thenReturn(Collections.emptyList());
        assertTrue(service.generateToday().getSummary().contains("记录"));
    }

    @Test
    void proteinLow() {
        when(meals.byDate(any())).thenReturn(Collections.singletonList(
            meal(MealType.BREAKFAST, "1300", "10")));
        assertTrue(service.generateToday().getNutritionAnalysis().contains("蛋白质"));
    }

    @Test
    void missingBreakfast() {
        when(meals.byDate(any())).thenReturn(Collections.singletonList(
            meal(MealType.LUNCH, "1300", "70")));
        assertTrue(service.generateToday().getNutritionAnalysis().contains("早餐"));
    }

    private static MealRecord meal(MealType type, String calories, String protein) {
        MealRecord record = new MealRecord();
        record.setMealType(type);
        record.setFoodName("普通饮食");
        record.setPortionDescription("一份");
        record.setCaloriesKcal(new BigDecimal(calories));
        record.setProteinG(new BigDecimal(protein));
        record.setCarbohydrateG(new BigDecimal("140"));
        record.setFatG(new BigDecimal("40"));
        return record;
    }
}
