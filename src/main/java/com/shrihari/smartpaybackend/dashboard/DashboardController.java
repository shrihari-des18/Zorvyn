package com.shrihari.smartpaybackend.dashboard;

import com.shrihari.smartpaybackend.common.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Dashboard analytics and summaries")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    public ApiResponse<?> getSummary() {
        return new ApiResponse<>(
                true,
                "Dashboard summary fetched",
                dashboardService.getSummary()
        );
    }

    @GetMapping("/weekly-summary")
    public ApiResponse<?> weeklySummary() {
        return new ApiResponse<>(
                true,
                "Weekly summary fetched",
                dashboardService.getWeeklySummary()
        );
    }

    @GetMapping("/chart")
    public ApiResponse<?> getChart() {
        return new ApiResponse<>(
                true,
                "OK",
                dashboardService.getChart()
        );
    }

    @GetMapping("/summary-detail")
    public ApiResponse<?> getSummaryDetail() {
        return new ApiResponse<>(
                true,
                "OK",
                dashboardService.getSummaryDetail()
        );
    }
}