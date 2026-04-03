package com.shrihari.smartpaybackend.risk;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class RiskCheckRequest {

    private BigDecimal amount;

    @JsonProperty("hour_of_day")
    private int hourOfDay;

    @JsonProperty("is_weekend")
    private int isWeekend;

    @JsonProperty("receiver_account_age_days")
    private int receiverAccountAgeDays;

    @JsonProperty("receiver_report_count")
    private int receiverReportCount;

    @JsonProperty("receiver_tx_count_24h")
    private int receiverTxCount24h;

    @JsonProperty("receiver_unique_senders_24h")
    private int receiverUniqueSenders24h;

    @JsonProperty("previous_connections_count")
    private int previousConnectionsCount;

    @JsonProperty("avg_transaction_amount_7d")
    private BigDecimal avgTransactionAmount7d;
}