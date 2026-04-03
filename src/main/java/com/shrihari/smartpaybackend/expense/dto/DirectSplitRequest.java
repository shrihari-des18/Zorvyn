package com.shrihari.smartpaybackend.expense.dto;

import com.shrihari.smartpaybackend.expense.SplitType;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class DirectSplitRequest {

    private String description;
    private BigDecimal amount;

    private Long payerId;

    private SplitType splitType;

    private List<Long> userIds;

    private List<ExactSplit> exactSplits;

    @Data
    public static class ExactSplit {
        private Long userId;
        private BigDecimal amount;
    }
}
