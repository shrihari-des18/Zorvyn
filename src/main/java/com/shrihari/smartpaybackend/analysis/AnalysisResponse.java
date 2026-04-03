package com.shrihari.smartpaybackend.analysis;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisResponse {

    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
    private BigDecimal netBalance;

    private String topSpendingCategory;
    private String mostFrequentCategory;

    private List<CategoryBreakdown> categoryBreakdown;

    private MonthlyComparison monthlyComparison;

    private BigDecimal weeklyAverage;
    private BigDecimal dailyAverage;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryBreakdown {
        private String category;
        private BigDecimal amount;
        private double percentage;
        private int transactionCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyComparison {
        private BigDecimal thisMonth;
        private BigDecimal lastMonth;
        private String trend;
        private BigDecimal percentageChange;
    }
}