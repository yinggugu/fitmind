package com.fitmind.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fitmind.entity.DailyHealthLetter;
import com.fitmind.entity.MealRecord;
import com.fitmind.enums.GenerationType;
import com.fitmind.enums.MealType;
import com.fitmind.mapper.DailyHealthLetterMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HealthLetterService {
    private final DailyHealthLetterMapper mapper;
    private final MealService mealService;
    private final QwenHealthLetterClient qwenClient;

    @Value("${fitmind.health.calorie-low-threshold:1200}") private BigDecimal calorieLow;
    @Value("${fitmind.health.protein-min-grams:60}") private BigDecimal proteinMin;
    @Value("${fitmind.health.carbohydrate-min-grams:120}") private BigDecimal carbMin;
    @Value("${fitmind.health.fat-max-grams:70}") private BigDecimal fatMax;

    public DailyHealthLetter findToday() { return find(LocalDate.now()); }
    public DailyHealthLetter generateToday() { return generate(LocalDate.now(), LocalDate.now().minusDays(1)); }

    public DailyHealthLetter generate(LocalDate report, LocalDate analysis) {
        DailyHealthLetter existing = find(report);
        if (existing != null) return existing;
        List<MealRecord> rows = mealService.byDate(analysis);
        DailyHealthLetter letter = baseLetter(report, analysis);
        if (rows.isEmpty()) {
            fillNoData(letter);
        } else {
            BigDecimal calories = sum(rows, "calorie");
            BigDecimal protein = sum(rows, "protein");
            BigDecimal carbs = sum(rows, "carb");
            BigDecimal fat = sum(rows, "fat");
            fillRuleLetter(letter, rows, calories, protein, carbs, fat);
            Optional<QwenHealthLetterClient.AiLetter> ai = qwenClient.generate(
                report, analysis, rows, calories, protein, carbs, fat);
            if (ai.isPresent()) {
                applyAi(letter, ai.get());
                letter.setGenerationType(GenerationType.AI);
            }
        }
        mapper.insert(letter);
        return letter;
    }

    private DailyHealthLetter baseLetter(LocalDate report, LocalDate analysis) {
        DailyHealthLetter letter = new DailyHealthLetter();
        letter.setUserId(ProfileService.USER_ID);
        letter.setReportDate(report);
        letter.setAnalysisDate(analysis);
        letter.setGenerationType(GenerationType.RULE);
        return letter;
    }

    private void fillNoData(DailyHealthLetter letter) {
        letter.setSummary("昨天的饮食记录还不完整，暂时无法准确回顾全天状态。没关系，记录本身就是逐渐养成的习惯，不需要因为漏记而责备自己。");
        letter.setCalorieAnalysis("由于缺少足够的餐次数据，今天不对总热量作判断，也不会用不完整的数字推测实际摄入。");
        letter.setNutritionAnalysis("今天可以照常安排三餐，尽量让主食、蔬菜和蛋白质食物都出现。等记录更完整后，FitMind 会提供更贴近实际情况的分析；所有营养数字仍只作为日常估算参考。");
        letter.setTodayFocus("今天只完整记录一餐，包括食物名称和大致份量。先把记录做得轻松、可持续。");
    }

    private void fillRuleLetter(DailyHealthLetter letter, List<MealRecord> rows,
                                BigDecimal calories, BigDecimal protein,
                                BigDecimal carbs, BigDecimal fat) {
        Set<MealType> types = rows.stream().map(MealRecord::getMealType).collect(Collectors.toSet());
        List<String> issues = issues(rows, calories, protein, carbs, fat, types);
        letter.setSummary(issues.isEmpty()
            ? "昨天的饮食记录整体比较稳定，餐次与食物搭配保持了较自然的节奏。继续维持这种不紧绷、能长期坚持的状态，比追求单日数字更重要。"
            : "昨天的记录已经提供了比较清楚的饮食轮廓，其中有一两处可以温和调整。单日数据会受到份量估算和食物做法影响，不必因为某个数字刻意少吃。");
        letter.setCalorieAnalysis("昨日估算总热量约 " + calories.setScale(0, RoundingMode.HALF_UP)
            + " kcal。这个结果来自份量描述与常见食物数据，只适合观察整体趋势，不代表精确摄入或医学结论。");
        letter.setNutritionAnalysis(issues.isEmpty()
            ? "蛋白质、碳水和脂肪的整体结构较平稳，三餐之间没有明显失衡。今天继续正常吃饭，并留意饥饿感和饱腹感即可。"
            : issues.subList(0, Math.min(2, issues.size())).stream()
                .map(issue -> HealthLetterTemplates.select(issue, letter.getReportDate()))
                .collect(Collectors.joining(" "))
                + " 这些营养值均为估算，重点是观察连续几天的变化，而不是评价某一餐吃得好不好。 ");
        letter.setTodayFocus(focus(issues));
    }

    private List<String> issues(List<MealRecord> rows, BigDecimal calories, BigDecimal protein,
                                BigDecimal carbs, BigDecimal fat, Set<MealType> types) {
        List<String> issues = new ArrayList<>();
        if (calories.compareTo(calorieLow) < 0) issues.add("总热量偏低");
        if (protein.compareTo(proteinMin) < 0) issues.add("蛋白质不足");
        if (carbs.compareTo(carbMin) < 0) issues.add("碳水不足");
        if (fat.compareTo(fatMax) > 0) issues.add("脂肪偏高");
        if (!types.contains(MealType.BREAKFAST)) issues.add("缺少早餐");
        if (types.size() == 1) issues.add("全天只有一餐");
        long fried = rows.stream().filter(row -> row.getFoodName().contains("炸")
            || row.getCaloriesKcal().compareTo(BigDecimal.valueOf(600)) > 0).count();
        if (fried > 1) issues.add("炸物或高热量食物较多");
        return issues;
    }

    private void applyAi(DailyHealthLetter target, QwenHealthLetterClient.AiLetter source) {
        target.setSummary(source.getSummary().trim());
        target.setCalorieAnalysis(source.getCalorieAnalysis().trim());
        target.setNutritionAnalysis(source.getNutritionAnalysis().trim());
        target.setTodayFocus(source.getTodayFocus().trim());
    }

    private DailyHealthLetter find(LocalDate date) {
        return mapper.selectOne(new LambdaQueryWrapper<DailyHealthLetter>()
            .eq(DailyHealthLetter::getUserId, ProfileService.USER_ID)
            .eq(DailyHealthLetter::getReportDate, date));
    }

    private BigDecimal sum(List<MealRecord> rows, String type) {
        BigDecimal result = BigDecimal.ZERO;
        for (MealRecord row : rows) {
            result = result.add("calorie".equals(type) ? row.getCaloriesKcal()
                : "protein".equals(type) ? row.getProteinG()
                : "carb".equals(type) ? row.getCarbohydrateG() : row.getFatG());
        }
        return result;
    }

    private String focus(List<String> issues) {
        if (issues.contains("蛋白质不足")) return "在午餐或晚餐增加一份鱼虾、鸡蛋、瘦肉或豆制品，其他食物照常吃。";
        if (issues.contains("缺少早餐")) return "明早准备一份简单早餐，例如鸡蛋配玉米或豆浆，不需要复杂。";
        if (issues.contains("总热量偏低") || issues.contains("全天只有一餐")) return "今天规律安排三餐，不要为了补偿昨天而继续减少食量。";
        if (issues.contains("碳水不足")) return "今天至少在一餐保留一拳左右的主食，让能量供应更稳定。";
        if (issues.contains("脂肪偏高") || issues.contains("炸物或高热量食物较多")) return "今天选择一餐以清蒸、炖煮或清炒为主，其他餐次保持正常。";
        return "保持正常三餐，并继续如实记录一整天的饮食。";
    }
}
