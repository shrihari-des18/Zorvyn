package com.shrihari.smartpaybackend.expense.dto;


import com.shrihari.smartpaybackend.expense.SplitType;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateExpenseRequest {

    private Long groupId;
    private String description;
    private BigDecimal amount;
    private SplitType splitType;
    private String category;
    private List<Long> userIds; // for EQUAL
    private List<ExactSplit> exactSplits; // for EXACT

    @Data
    public static class ExactSplit {
        private Long userId;
        private BigDecimal amount;
    }
}


