package com.fitmind.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("body_measurement")
public class BodyMeasurement {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private LocalDate recordDate;
    private BigDecimal heightCm;
    private BigDecimal weightKg;
    private BigDecimal upperChestCm;
    private BigDecimal underChestCm;
    private BigDecimal waistCm;
    private BigDecimal abdomenCm;
    private BigDecimal shoulderWidthCm;
    private BigDecimal thighCm;
    private BigDecimal calfCm;
    private BigDecimal ankleCm;
    private BigDecimal upperArmCm;
    private BigDecimal wristCm;
    private BigDecimal thighLengthCm;
    private BigDecimal calfLengthCm;
    private BigDecimal upperArmLengthCm;
    private BigDecimal forearmLengthCm;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
