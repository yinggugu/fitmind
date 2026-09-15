package com.fitmind.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealthToolResult {
    private String status;
    private LocalDate queryStartDate;
    private LocalDate queryEndDate;
    private int recordCount;
    private int expectedDayCount;
    private int recordedDayCount;
    private boolean missingData;
    @Builder.Default
    private List<LocalDate> missingDates = new ArrayList<>();
    private String message;
    private Object data;
}
