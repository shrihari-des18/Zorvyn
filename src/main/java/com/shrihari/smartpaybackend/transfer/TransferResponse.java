package com.shrihari.smartpaybackend.transfer;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class TransferResponse {

    private Long transferId;
    private String upiLink;
    private BigDecimal amount;
    private String toUserName;
    private String toUpiId;
    private String status;
}