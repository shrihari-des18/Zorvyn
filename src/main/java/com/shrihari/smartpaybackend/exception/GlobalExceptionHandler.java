package com.shrihari.smartpaybackend.exception;

import com.shrihari.smartpaybackend.common.ApiResponse;
import com.shrihari.smartpaybackend.risk.RiskBlockedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<?>> handleApiException(ApiException ex) {
        log.warn("ApiException: {}", ex.getMessage());
        return new ResponseEntity<>(
                new ApiResponse<>(false, ex.getMessage(), null),
                ex.getStatus()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleGeneralException(Exception ex) {

        log.error("Unexpected error: {}", ex.getMessage(), ex);

        return new ResponseEntity<>(
                new ApiResponse<>(false, "Something went wrong", null),
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }
    @ExceptionHandler(RiskBlockedException.class)
    public ResponseEntity<ApiResponse<?>> handleRiskBlocked(RiskBlockedException ex) {

        log.warn("Transaction blocked by risk engine: {}", ex.getMessage());

        java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("riskLevel", ex.getRiskLevel());
        data.put("riskScore", ex.getRiskScore());
        data.put("blocked", true);
        data.put("riskReasons", ex.getRiskReasons());

        return new ResponseEntity<>(
                new ApiResponse<>(false, ex.getMessage(), data),
                HttpStatus.FORBIDDEN
        );
    }

}