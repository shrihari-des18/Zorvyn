package com.shrihari.smartpaybackend.report;

import com.shrihari.smartpaybackend.common.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "Scam reporting")
public class ScamReportController {

    private final ScamReportService scamReportService;

    @PostMapping
    public ApiResponse<?> reportUser(@RequestBody ReportRequest request) {

        scamReportService.reportUser(
                request.getReportedUserId(),
                request.getReason()
        );

        return new ApiResponse<>(true, "User reported successfully", null);
    }

    @Data
    static class ReportRequest {
        private Long reportedUserId;
        private String reason;
    }
}