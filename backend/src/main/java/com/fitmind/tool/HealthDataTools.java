package com.fitmind.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitmind.entity.BodyMeasurement;
import com.fitmind.entity.MealRecord;
import com.fitmind.entity.UserProfile;
import com.fitmind.entity.WeightRecord;
import com.fitmind.exception.BusinessException;
import com.fitmind.service.BodyMeasurementService;
import com.fitmind.service.DashboardService;
import com.fitmind.service.MealService;
import com.fitmind.service.ProfileService;
import com.fitmind.service.WeightService;
import com.fitmind.vo.DashboardOverviewVO;
import com.fitmind.vo.NutritionStatisticsVO;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class HealthDataTools {
    private static final Logger log = LoggerFactory.getLogger(HealthDataTools.class);
    private static final int MAX_WEIGHT_RANGE_DAYS = 366;
    private static final int MAX_MEAL_RANGE_DAYS = 90;

    private final DashboardService dashboardService;
    private final WeightService weightService;
    private final MealService mealService;
    private final BodyMeasurementService bodyMeasurementService;
    private final ProfileService profileService;
    private final ObjectMapper objectMapper;

    @Tool("查询当前健康概览，包括当前、起始、目标体重，距离目标、近7次体重变化和目标进度。所有重量单位均为kg")
    public String getCurrentHealthOverview() {
        return execute("getCurrentHealthOverview", Collections.emptyMap(), () -> {
            DashboardOverviewVO overview = dashboardService.overview();
            List<WeightRecord> records = overview.getWeightTrend();
            LocalDate start = records.isEmpty() ? null : records.get(0).getRecordDate();
            LocalDate end = records.isEmpty() ? null : records.get(records.size() - 1).getRecordDate();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("currentWeightKg", overview.getCurrentWeightKg());
            data.put("startWeightKg", overview.getStartWeightKg());
            data.put("targetWeightKg", overview.getTargetWeightKg());
            data.put("distanceToTargetKg", overview.getDistanceToTargetKg());
            data.put("change7dKg", overview.getChange7dKg());
            data.put("progressPercent", overview.getProgressPercent());
            data.put("preferredWeightUnit", overview.getPreferredWeightUnit());
            data.put("weightTrend", weightRows(records));
            List<LocalDate> missing = missingDates(start, end, weightDates(records));
            return result(start, end, records.size(), records.size(), records.isEmpty() || !missing.isEmpty(),
                records.isEmpty() ? "当前没有体重记录，概览中的当前体重采用用户起始体重。" : "概览数据来自用户资料和最新体重记录。", data,
                missing);
        });
    }

    @Tool("查询最近7条或30条体重趋势，并由Java计算首末变化、平均值、最高值和最低值")
    public String getRecentWeightTrend(@P("只能填写7或30") int days) {
        Map<String, Object> args = oneArg("days", days);
        return execute("getRecentWeightTrend", args, () -> {
            if (days != 7 && days != 30) throw new IllegalArgumentException("days只能是7或30");
            List<WeightRecord> records = weightService.trend(days);
            if (records.isEmpty()) return insufficient(null, null, days, "没有可用的体重记录");
            LocalDate start = records.get(0).getRecordDate();
            LocalDate end = records.get(records.size() - 1).getRecordDate();
            List<LocalDate> missing = missingDates(start, end, weightDates(records));
            Map<String, Object> data = weightAnalysis(records);
            data.put("records", weightRows(records));
            boolean missingData = records.size() < days || !missing.isEmpty();
            return result(start, end, records.size(), days, missingData,
                missingData ? "返回最新可用记录，但数量不足或日期不连续。" : "返回最新" + days + "条连续体重记录。",
                data, missing);
        });
    }

    @Tool("查询指定日期的全部饮食记录和当日汇总。日期格式必须为yyyy-MM-dd")
    public String getMealsByDate(@P("日期，格式yyyy-MM-dd") String date) {
        Map<String, Object> args = oneArg("date", date);
        return execute("getMealsByDate", args, () -> {
            LocalDate target = parseDate(date);
            List<MealRecord> records = mealService.byDate(target);
            if (records.isEmpty()) return insufficient(target, target, 1, "该日期没有饮食记录");
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("records", mealRows(records));
            data.put("dailyTotals", mealTotals(records));
            return result(target, target, records.size(), 1, false,
                "营养数值来自饮食记录，可能包含估算数据。", data, Collections.emptyList());
        });
    }

    @Tool("查询最近N天营养汇总。N只能为1到30，结束日期以数据库中最新饮食日期为准，汇总数字由Java计算")
    public String getRecentNutritionSummary(@P("最近天数，1到30") int days) {
        Map<String, Object> args = oneArg("days", days);
        return execute("getRecentNutritionSummary", args, () -> {
            requireDays(days, 30);
            MealRecord latest = mealService.latest();
            if (latest == null) return insufficient(null, null, days, "没有可用的饮食记录");
            LocalDate end = latest.getRecordDate();
            LocalDate start = end.minusDays(days - 1L);
            List<MealRecord> records = mealService.listBetween(start, end);
            NutritionStatisticsVO statistics = mealService.nutritionBetween(start, end);
            Set<LocalDate> dates = mealDates(records);
            List<LocalDate> missing = missingDates(start, end, dates);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("statistics", statistics);
            data.put("dailyCaloriesKcal", dailyCalories(records));
            data.put("containsEstimatedData", records.stream().anyMatch(r -> Boolean.TRUE.equals(r.getEstimated())));
            return result(start, end, records.size(), days, !missing.isEmpty(),
                missing.isEmpty() ? "营养汇总覆盖完整日期范围，数值可能包含估算。" : "部分日期没有饮食记录，汇总仅基于已有数据。",
                data, missing);
        });
    }

    @Tool("查询数据库中最新一条身体画像维度，包括身高、体重、胸围、腰腹围、肩宽、四肢围度和长度")
    public String getLatestBodyMeasurements() {
        return execute("getLatestBodyMeasurements", Collections.emptyMap(), () -> {
            try {
                BodyMeasurement body = bodyMeasurementService.latest();
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("recordDate", body.getRecordDate());
                data.put("heightCm", body.getHeightCm());
                data.put("weightKg", body.getWeightKg());
                data.put("upperChestCm", body.getUpperChestCm());
                data.put("underChestCm", body.getUnderChestCm());
                data.put("waistCm", body.getWaistCm());
                data.put("abdomenCm", body.getAbdomenCm());
                data.put("shoulderWidthCm", body.getShoulderWidthCm());
                data.put("thighCm", body.getThighCm());
                data.put("calfCm", body.getCalfCm());
                data.put("ankleCm", body.getAnkleCm());
                data.put("upperArmCm", body.getUpperArmCm());
                data.put("wristCm", body.getWristCm());
                data.put("thighLengthCm", body.getThighLengthCm());
                data.put("calfLengthCm", body.getCalfLengthCm());
                data.put("upperArmLengthCm", body.getUpperArmLengthCm());
                data.put("forearmLengthCm", body.getForearmLengthCm());
                return result(body.getRecordDate(), body.getRecordDate(), 1, 1, false,
                    "返回最新身体画像；围度和长度单位为cm，体重单位为kg。", data, Collections.emptyList());
            } catch (BusinessException exception) {
                if (exception.getCode() == 404) return insufficient(null, null, 1, "尚无身体维度记录");
                throw exception;
            }
        });
    }

    @Tool("查询用户目标体重、起始体重和偏好显示单位。重量数字统一为kg")
    public String getWeightGoal() {
        return execute("getWeightGoal", Collections.emptyMap(), () -> {
            UserProfile profile = profileService.get();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("startWeightKg", profile.getStartWeightKg());
            data.put("targetWeightKg", profile.getTargetWeightKg());
            data.put("preferredWeightUnit", profile.getPreferredWeightUnit());
            return result(null, null, 1, 1, false, "目标数据来自当前用户资料。", data, Collections.emptyList());
        });
    }

    @Tool("计算指定日期范围的体重变化、平均值、最高值和最低值。日期格式为yyyy-MM-dd，所有计算由Java完成")
    public String calculateWeightChange(
        @P("开始日期，格式yyyy-MM-dd") String startDate,
        @P("结束日期，格式yyyy-MM-dd") String endDate) {
        Map<String, Object> args = rangeArgs(startDate, endDate);
        return execute("calculateWeightChange", args, () -> {
            LocalDate start = parseDate(startDate);
            LocalDate end = parseDate(endDate);
            validateRange(start, end, MAX_WEIGHT_RANGE_DAYS);
            List<WeightRecord> records = weightService.listBetween(start, end);
            if (records.size() < 2) {
                return insufficient(start, end, expectedDays(start, end), records.size(), "范围内至少需要两条体重记录才能计算变化");
            }
            List<LocalDate> missing = missingDates(start, end, weightDates(records));
            Map<String, Object> data = weightAnalysis(records);
            data.put("records", weightRows(records));
            return result(start, end, records.size(), expectedDays(start, end), !missing.isEmpty(),
                missing.isEmpty() ? "体重变化基于完整日期范围。" : "部分日期缺少体重，变化量按范围内首末两条记录计算。",
                data, missing);
        });
    }

    @Tool("查询指定日期范围的全部饮食记录和每日热量汇总。日期格式为yyyy-MM-dd，最长90天")
    public String getMealsInRange(
        @P("开始日期，格式yyyy-MM-dd") String startDate,
        @P("结束日期，格式yyyy-MM-dd") String endDate) {
        Map<String, Object> args = rangeArgs(startDate, endDate);
        return execute("getMealsInRange", args, () -> {
            LocalDate start = parseDate(startDate);
            LocalDate end = parseDate(endDate);
            validateRange(start, end, MAX_MEAL_RANGE_DAYS);
            List<MealRecord> records = mealService.listBetween(start, end);
            if (records.isEmpty()) return insufficient(start, end, expectedDays(start, end), "范围内没有饮食记录");
            List<LocalDate> missing = missingDates(start, end, mealDates(records));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("records", mealRows(records));
            data.put("dailyCaloriesKcal", dailyCalories(records));
            data.put("rangeTotals", mealTotals(records));
            return result(start, end, records.size(), expectedDays(start, end), !missing.isEmpty(),
                missing.isEmpty() ? "范围内每天都有饮食记录，营养数值可能包含估算。" : "部分日期没有饮食记录，不能把空缺日期当作未进食。",
                data, missing);
        });
    }

    @Tool("综合查询最近N天的体重和饮食，返回Java计算的体重变化、营养汇总、每日热量和最高热量日期。N只能为1到30")
    public String analyzeRecentHealth(@P("最近天数，1到30") int days) {
        Map<String, Object> args = oneArg("days", days);
        return execute("analyzeRecentHealth", args, () -> {
            requireDays(days, 30);
            WeightRecord latestWeight = weightService.latest();
            MealRecord latestMeal = mealService.latest();
            if (latestWeight == null && latestMeal == null) return insufficient(null, null, days, "没有可用的体重或饮食记录");
            LocalDate end = latestDate(latestWeight, latestMeal);
            LocalDate start = end.minusDays(days - 1L);
            List<WeightRecord> weights = weightService.listBetween(start, end);
            List<MealRecord> meals = mealService.listBetween(start, end);
            NutritionStatisticsVO nutrition = mealService.nutritionBetween(start, end);
            Map<LocalDate, BigDecimal> dailyCalories = dailyCalories(meals);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("weight", weights.size() < 2 ? Collections.singletonMap("message", "体重记录不足两条，无法计算变化") : weightAnalysis(weights));
            data.put("weightRecords", weightRows(weights));
            data.put("nutrition", nutrition);
            data.put("dailyCaloriesKcal", dailyCalories);
            data.put("highestCalorieDay", highestCalorieDay(dailyCalories));
            data.put("mealItemCount", meals.size());
            data.put("containsEstimatedData", meals.stream().anyMatch(r -> Boolean.TRUE.equals(r.getEstimated())));
            Set<LocalDate> completeDates = new LinkedHashSet<>(weightDates(weights));
            completeDates.retainAll(mealDates(meals));
            List<LocalDate> missing = missingDates(start, end, completeDates);
            boolean missingData = weights.size() < 2 || meals.isEmpty() || !missing.isEmpty();
            int recordCount = weights.size() + meals.size();
            return result(start, end, recordCount, days, missingData,
                missingData ? "综合结果仅基于现有记录；missingDates表示体重或饮食至少一类缺失。" : "体重与饮食数据覆盖完整日期范围。",
                data, missing);
        });
    }

    private String execute(String toolName, Map<String, Object> args, Supplier<HealthToolResult> action) {
        long startedAt = System.nanoTime();
        String traceId = MDC.get("assistantTraceId");
        try {
            HealthToolResult value = action.get();
            log.info("AI_TOOL traceId={} tool={} args={} elapsedMs={} success=true status={}",
                traceId, toolName, safeJson(args), elapsedMs(startedAt), value.getStatus());
            return safeJson(value);
        } catch (Exception exception) {
            String reason = safeReason(exception);
            log.warn("AI_TOOL traceId={} tool={} args={} elapsedMs={} success=false errorType={} reason={}",
                traceId, toolName, safeJson(args), elapsedMs(startedAt), exception.getClass().getSimpleName(), reason);
            HealthToolResult error = HealthToolResult.builder()
                .status("ERROR").recordCount(0).expectedDayCount(0).recordedDayCount(0)
                .missingData(true).message("查询失败：" + reason).build();
            return safeJson(error);
        }
    }

    private HealthToolResult result(LocalDate start, LocalDate end, int recordCount, int expectedDays,
                                    boolean missingData, String message, Object data, List<LocalDate> missingDates) {
        int recordedDays = start == null || end == null ? 0 : expectedDays(start, end) - missingDates.size();
        return HealthToolResult.builder()
            .status(missingData ? "PARTIAL" : "SUCCESS")
            .queryStartDate(start).queryEndDate(end).recordCount(recordCount)
            .expectedDayCount(expectedDays).recordedDayCount(Math.max(0, recordedDays))
            .missingData(missingData).missingDates(missingDates).message(message).data(data).build();
    }

    private HealthToolResult insufficient(LocalDate start, LocalDate end, int expectedDays, String message) {
        return insufficient(start, end, expectedDays, 0, message);
    }

    private HealthToolResult insufficient(LocalDate start, LocalDate end, int expectedDays, int recordCount, String message) {
        return HealthToolResult.builder().status("DATA_INSUFFICIENT")
            .queryStartDate(start).queryEndDate(end).recordCount(recordCount)
            .expectedDayCount(expectedDays).recordedDayCount(0).missingData(true)
            .missingDates(start == null || end == null ? Collections.emptyList() : allDates(start, end))
            .message(message).data(Collections.emptyMap()).build();
    }

    private Map<String, Object> weightAnalysis(List<WeightRecord> records) {
        Map<String, Object> data = new LinkedHashMap<>();
        WeightRecord first = records.get(0);
        WeightRecord last = records.get(records.size() - 1);
        BigDecimal total = records.stream().map(WeightRecord::getWeightKg).reduce(BigDecimal.ZERO, BigDecimal::add);
        Comparator<WeightRecord> byWeight = Comparator.comparing(WeightRecord::getWeightKg);
        WeightRecord minimum = records.stream().min(byWeight).orElse(first);
        WeightRecord maximum = records.stream().max(byWeight).orElse(first);
        data.put("firstRecordDate", first.getRecordDate());
        data.put("firstWeightKg", first.getWeightKg());
        data.put("lastRecordDate", last.getRecordDate());
        data.put("lastWeightKg", last.getWeightKg());
        data.put("changeKg", last.getWeightKg().subtract(first.getWeightKg()).setScale(2, RoundingMode.HALF_UP));
        data.put("averageWeightKg", total.divide(BigDecimal.valueOf(records.size()), 2, RoundingMode.HALF_UP));
        data.put("minimumWeightKg", minimum.getWeightKg());
        data.put("minimumWeightDate", minimum.getRecordDate());
        data.put("maximumWeightKg", maximum.getWeightKg());
        data.put("maximumWeightDate", maximum.getRecordDate());
        return data;
    }

    private List<Map<String, Object>> weightRows(List<WeightRecord> records) {
        return records.stream().map(record -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("recordDate", record.getRecordDate());
            row.put("recordTime", record.getRecordTime());
            row.put("weightKg", record.getWeightKg());
            return row;
        }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> mealRows(List<MealRecord> records) {
        return records.stream().map(record -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("recordDate", record.getRecordDate());
            row.put("mealType", record.getMealType());
            row.put("foodName", record.getFoodName());
            row.put("portionDescription", record.getPortionDescription());
            row.put("caloriesKcal", zero(record.getCaloriesKcal()));
            row.put("proteinG", zero(record.getProteinG()));
            row.put("carbohydrateG", zero(record.getCarbohydrateG()));
            row.put("fatG", zero(record.getFatG()));
            row.put("estimated", record.getEstimated());
            row.put("sourceType", record.getSourceType());
            return row;
        }).collect(Collectors.toList());
    }

    private Map<String, BigDecimal> mealTotals(List<MealRecord> records) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        totals.put("caloriesKcal", sum(records, "calories"));
        totals.put("proteinG", sum(records, "protein"));
        totals.put("carbohydrateG", sum(records, "carbohydrate"));
        totals.put("fatG", sum(records, "fat"));
        return totals;
    }

    private BigDecimal sum(List<MealRecord> records, String type) {
        BigDecimal total = BigDecimal.ZERO;
        for (MealRecord record : records) {
            if ("calories".equals(type)) total = total.add(zero(record.getCaloriesKcal()));
            else if ("protein".equals(type)) total = total.add(zero(record.getProteinG()));
            else if ("carbohydrate".equals(type)) total = total.add(zero(record.getCarbohydrateG()));
            else total = total.add(zero(record.getFatG()));
        }
        return total;
    }

    private Map<LocalDate, BigDecimal> dailyCalories(List<MealRecord> records) {
        Map<LocalDate, BigDecimal> values = new LinkedHashMap<>();
        for (MealRecord record : records) {
            values.put(record.getRecordDate(), values.getOrDefault(record.getRecordDate(), BigDecimal.ZERO)
                .add(zero(record.getCaloriesKcal())));
        }
        return values;
    }

    private Map<String, Object> highestCalorieDay(Map<LocalDate, BigDecimal> values) {
        if (values.isEmpty()) return Collections.emptyMap();
        Map.Entry<LocalDate, BigDecimal> highest = values.entrySet().stream()
            .max(Map.Entry.comparingByValue()).orElse(null);
        Map<String, Object> data = new LinkedHashMap<>();
        if (highest != null) {
            data.put("recordDate", highest.getKey());
            data.put("caloriesKcal", highest.getValue());
        }
        return data;
    }

    private LocalDate latestDate(WeightRecord weight, MealRecord meal) {
        if (weight == null) return meal.getRecordDate();
        if (meal == null) return weight.getRecordDate();
        return weight.getRecordDate().isAfter(meal.getRecordDate()) ? weight.getRecordDate() : meal.getRecordDate();
    }

    private Set<LocalDate> weightDates(List<WeightRecord> records) {
        return records.stream().map(WeightRecord::getRecordDate).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<LocalDate> mealDates(List<MealRecord> records) {
        return records.stream().map(MealRecord::getRecordDate).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<LocalDate> missingDates(LocalDate start, LocalDate end, Set<LocalDate> recordedDates) {
        if (start == null || end == null) return Collections.emptyList();
        List<LocalDate> missing = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            if (!recordedDates.contains(date)) missing.add(date);
        }
        return missing;
    }

    private List<LocalDate> allDates(LocalDate start, LocalDate end) {
        return missingDates(start, end, Collections.emptySet());
    }

    private int expectedDays(LocalDate start, LocalDate end) {
        return (int) ChronoUnit.DAYS.between(start, end) + 1;
    }

    private void validateRange(LocalDate start, LocalDate end, int maxDays) {
        if (start.isAfter(end)) throw new IllegalArgumentException("开始日期不能晚于结束日期");
        if (expectedDays(start, end) > maxDays) throw new IllegalArgumentException("日期范围不能超过" + maxDays + "天");
    }

    private void requireDays(int days, int maxDays) {
        if (days < 1 || days > maxDays) throw new IllegalArgumentException("days必须在1到" + maxDays + "之间");
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException | NullPointerException exception) {
            throw new IllegalArgumentException("日期格式必须为yyyy-MM-dd");
        }
    }

    private Map<String, Object> oneArg(String name, Object value) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put(name, value);
        return args;
    }

    private Map<String, Object> rangeArgs(String startDate, String endDate) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("startDate", startDate);
        args.put("endDate", endDate);
        return args;
    }

    private String safeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{\"status\":\"ERROR\",\"missingData\":true,\"message\":\"结果序列化失败\"}";
        }
    }

    private String safeReason(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.trim().isEmpty()) return exception.getClass().getSimpleName();
        String cleaned = message.replaceAll("[\r\n\t]+", " ").trim();
        return cleaned.length() > 120 ? cleaned.substring(0, 120) : cleaned;
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
