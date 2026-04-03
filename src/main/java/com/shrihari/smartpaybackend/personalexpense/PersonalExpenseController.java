package com.shrihari.smartpaybackend.personalexpense;

import com.shrihari.smartpaybackend.common.ApiResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/personal-expenses")
@RequiredArgsConstructor
public class PersonalExpenseController {

    private final PersonalExpenseService personalExpenseService;

    @PostMapping
    public ApiResponse<?> createExpense(@RequestBody CreatePersonalExpenseRequest request) {

        PersonalExpense expense = personalExpenseService.createPersonalExpense(
                request.getDescription(),
                request.getAmount(),
                request.getCategory(),
                request.getNote(),
                request.getExpenseDate()
        );

        return new ApiResponse<>(true, "Personal expense recorded", expense);
    }

    @GetMapping("/my")
    public ApiResponse<?> getMyExpenses() {

        return new ApiResponse<>(
                true,
                "Personal expenses fetched",
                personalExpenseService.getMyPersonalExpenses()
        );
    }

    @Data
    static class CreatePersonalExpenseRequest {
        private String description;
        private BigDecimal amount;
        private String category;
        private String note;
        private LocalDateTime expenseDate; // optional, defaults to now
    }
}