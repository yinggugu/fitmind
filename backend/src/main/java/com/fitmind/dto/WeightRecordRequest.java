package com.fitmind.dto; import lombok.Data; import javax.validation.constraints.*; import java.math.BigDecimal; import java.time.*;
@Data public class WeightRecordRequest { @NotNull private LocalDate recordDate; private LocalTime recordTime=LocalTime.of(7,0); @NotNull @DecimalMin(value="0.01") private BigDecimal weightKg; }
