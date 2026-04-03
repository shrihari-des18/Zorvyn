package com.shrihari.smartpaybackend.expense.dto;

import com.shrihari.smartpaybackend.expense.SplitType;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class EditExpenseRequest {

    private String description;
    private BigDecimal amount;
    private Long payerId;
    private SplitType splitType;

    private List<Long> userIds; // for EQUAL
    private List<DirectSplitRequest.ExactSplit> exactSplits; // for EXACT
}
