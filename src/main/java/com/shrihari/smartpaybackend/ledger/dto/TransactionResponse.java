package com.shrihari.smartpaybackend.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {

    private String type; // EXPENSE or SETTLEMENT
    private BigDecimal amount;
    private String description;
    private LocalDateTime createdAt;

    private Long fromUserId;
    private String fromUserName;

    private Long toUserId;
    private String toUserName;
    private String perspective; // "SPENT" = you paid out, "OWED" = someone owes you
}
