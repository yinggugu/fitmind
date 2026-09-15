package com.fitmind.entity;
import com.baomidou.mybatisplus.annotation.*; import com.fitmind.enums.GenerationType; import lombok.Data; import java.time.*;
@Data @TableName("daily_health_letter") public class DailyHealthLetter { @TableId(type=IdType.AUTO) private Long id; private Long userId; private LocalDate reportDate; private LocalDate analysisDate; private String summary; private String calorieAnalysis; private String nutritionAnalysis; private String todayFocus; @EnumValue private GenerationType generationType; private LocalDateTime createdAt; private LocalDateTime updatedAt; }
