package com.shrihari.smartpaybackend.risk;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class RiskCheckResponse {

    @JsonProperty("fraud_risk_score")
    private double fraudRiskScore;

    @JsonProperty("risk_level")
    private String riskLevel;

    @JsonProperty("is_blocked")
    private boolean isBlocked;

    private String message;

    private List<String> riskReasons;
}