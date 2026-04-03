package com.shrihari.smartpaybackend.risk;

import lombok.Getter;

import java.util.List;

@Getter
public class RiskBlockedException extends RuntimeException {

    private final String riskLevel;
    private final double riskScore;
    private final List<String> riskReasons;

    public RiskBlockedException(
            String message,
            String riskLevel,
            double riskScore,
            List<String> riskReasons) {
        super(message);
        this.riskLevel = riskLevel;
        this.riskScore = riskScore;
        this.riskReasons = riskReasons;
    }
}