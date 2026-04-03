package com.shrihari.smartpaybackend.ledger.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class SimplifiedDebtResponse {

    private Long fromUserId;
    private String fromUserName;

    private Long toUserId;
    private String toUserName;

    private BigDecimal amount;
}
