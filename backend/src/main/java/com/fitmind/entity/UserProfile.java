package com.fitmind.entity;
import com.baomidou.mybatisplus.annotation.*; import com.fitmind.enums.WeightUnit; import lombok.Data; import java.math.BigDecimal; import java.time.LocalDateTime;
@Data @TableName("user_profile") public class UserProfile { @TableId(type=IdType.AUTO) private Long id; private String nickname; private BigDecimal startWeightKg; private BigDecimal targetWeightKg; @EnumValue private WeightUnit preferredWeightUnit; private LocalDateTime createdAt; private LocalDateTime updatedAt; }
