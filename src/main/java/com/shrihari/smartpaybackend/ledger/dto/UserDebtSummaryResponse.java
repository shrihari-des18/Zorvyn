package com.shrihari.smartpaybackend.ledger.dto;


import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class UserDebtSummaryResponse {

    private Long userId;
    private String userName;
    private BigDecimal amount;
}
