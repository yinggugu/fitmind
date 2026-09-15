package com.fitmind.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@EnabledIfSystemProperty(named = "fitmind.live-db-test", matches = "true")
class HealthDataToolsDatabaseIT {
    @Autowired
    private HealthDataTools healthDataTools;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void allReadOnlyToolsCanQueryCurrentDatabase() throws Exception {
        List<String> results = Arrays.asList(
            healthDataTools.getCurrentHealthOverview(),
            healthDataTools.getRecentWeightTrend(7),
            healthDataTools.getMealsByDate("2026-08-11"),
            healthDataTools.getRecentNutritionSummary(7),
            healthDataTools.getLatestBodyMeasurements(),
            healthDataTools.getWeightGoal(),
            healthDataTools.calculateWeightChange("2026-08-06", "2026-08-17"),
            healthDataTools.getMealsInRange("2026-08-11", "2026-08-17"),
            healthDataTools.analyzeRecentHealth(14)
        );

        for (String result : results) {
            JsonNode json = objectMapper.readTree(result);
            assertNotEquals("ERROR", json.path("status").asText(), result);
            assertTrue(json.has("recordCount"), result);
            assertTrue(json.has("missingData"), result);
            assertTrue(json.has("data"), result);
        }
    }
}
