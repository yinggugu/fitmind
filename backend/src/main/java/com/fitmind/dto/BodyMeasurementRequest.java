package com.fitmind.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class BodyMeasurementRequest {
    @NotNull
    private LocalDate recordDate;
    @NotNull @DecimalMin("120") @DecimalMax("220")
    private BigDecimal heightCm;
    @NotNull @DecimalMin("25") @DecimalMax("250")
    private BigDecimal weightKg;
    @NotNull @DecimalMin("50") @DecimalMax("180")
    private BigDecimal upperChestCm;
    @NotNull @DecimalMin("45") @DecimalMax("170")
    private BigDecimal underChestCm;
    @NotNull @DecimalMin("45") @DecimalMax("180")
    private BigDecimal waistCm;
    @NotNull @DecimalMin("45") @DecimalMax("200")
    private BigDecimal abdomenCm;
    @NotNull @DecimalMin("25") @DecimalMax("70")
    private BigDecimal shoulderWidthCm;
    @NotNull @DecimalMin("25") @DecimalMax("100")
    private BigDecimal thighCm;
    @NotNull @DecimalMin("18") @DecimalMax("70")
    private BigDecimal calfCm;
    @NotNull @DecimalMin("12") @DecimalMax("45")
    private BigDecimal ankleCm;
    @NotNull @DecimalMin("15") @DecimalMax("65")
    private BigDecimal upperArmCm;
    @NotNull @DecimalMin("10") @DecimalMax("35")
    private BigDecimal wristCm;
    @NotNull @DecimalMin("20") @DecimalMax("70")
    private BigDecimal thighLengthCm;
    @NotNull @DecimalMin("20") @DecimalMax("65")
    private BigDecimal calfLengthCm;
    @NotNull @DecimalMin("15") @DecimalMax("55")
    private BigDecimal upperArmLengthCm;
    @NotNull @DecimalMin("15") @DecimalMax("55")
    private BigDecimal forearmLengthCm;
}
