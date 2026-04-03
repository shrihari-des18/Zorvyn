package com.shrihari.smartpaybackend.settlement;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class SettlementInitiateResponse {

    private Long settlementId;
    private BigDecimal amount;
    private String upiLink;
    private Boolean upiAvailable;
}