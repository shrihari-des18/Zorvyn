package com.shrihari.smartpaybackend.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardChartResponse {

    private List<String> labels;
    private List<BigDecimal> income;
    private List<BigDecimal> expense;
}