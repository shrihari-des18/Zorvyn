package com.shrihari.smartpaybackend.analysis;

import com.shrihari.smartpaybackend.common.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/analysis")
@RequiredArgsConstructor
@Tag(name = "Analysis", description = "Financial analysis and insights")
public class AnalysisController {

    private final AnalysisService analysisService;

    @GetMapping("/me")
    public ApiResponse<?> getMyAnalysis() {
        return new ApiResponse<>(
                true,
                "Analysis fetched",
                analysisService.getAnalysisForCurrentUser()
        );
    }
}