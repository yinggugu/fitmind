package com.fitmind.vo;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ImageAnalysisVO {
    private String foodName;
    private String portionDescription;
    private BigDecimal caloriesKcal;
    private BigDecimal proteinG;
    private BigDecimal carbohydrateG;
    private BigDecimal fatG;
    private Boolean estimated = true;
    private String sourceType = "AI_IMAGE";
}
