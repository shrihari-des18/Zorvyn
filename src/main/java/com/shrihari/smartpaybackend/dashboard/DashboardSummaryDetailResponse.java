package com.shrihari.smartpaybackend.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryDetailResponse {

    private Balance balance;
    private List<PendingRequest> pendingRequests;
    private List<RecentActivity> recentActivity;
    private Summary summary;
    private ExpenseBreakdown expenseBreakdown;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Balance {
        private BigDecimal total;
        private BigDecimal changePercent;
        private BigDecimal monthlySpent;
        private BigDecimal monthlyLimit;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PendingRequest {
        private Long id;
        private String name;
        private String description;
        private BigDecimal amount;
        private String type;
        private LocalDate dueDate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentActivity {
        private Long id;
        private String description;
        private BigDecimal amount;
        private String type;
        private LocalDateTime createdAt;
        private String fromUserName;
        private String toUserName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private BigDecimal totalBalance;
        private BigDecimal totalOwes;
        private BigDecimal totalIsOwed;
        private int groupCount;
        private int friendCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExpenseBreakdown {
        private BigDecimal thisMonth;
        private BigDecimal lastMonth;
        private String trend;
        private BigDecimal percentageChange;
    }
}