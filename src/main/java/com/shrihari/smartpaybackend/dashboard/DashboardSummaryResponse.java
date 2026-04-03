package com.shrihari.smartpaybackend.dashboard;


import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class DashboardSummaryResponse {

    private BigDecimal totalBalance;
}