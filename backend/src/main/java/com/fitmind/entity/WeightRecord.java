package com.fitmind.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.math.BigDecimal; import java.time.*;
@Data @TableName("weight_record") public class WeightRecord { @TableId(type=IdType.AUTO) private Long id; private Long userId; private LocalDate recordDate; private LocalTime recordTime; private BigDecimal weightKg; private LocalDateTime createdAt; private LocalDateTime updatedAt; }
