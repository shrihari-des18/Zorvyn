package com.shrihari.smartpaybackend.expense.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ExpenseResponse {

    private Long id;
    private String description;
    private BigDecimal totalAmount;
    private String paidBy;
    private LocalDateTime createdAt;
    private List<SplitResponse> splits;

    @Data
    @Builder
    public static class SplitResponse {
        private Long userId;
        private BigDecimal amountOwed;
    }
}

