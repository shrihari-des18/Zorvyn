package com.shrihari.smartpaybackend.expense;

import com.shrihari.smartpaybackend.common.ApiResponse;
import com.shrihari.smartpaybackend.exception.ApiException;
import com.shrihari.smartpaybackend.expense.dto.CreateExpenseRequest;
import com.shrihari.smartpaybackend.expense.dto.DirectSplitRequest;
import com.shrihari.smartpaybackend.expense.dto.EditExpenseRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/expenses")
@RequiredArgsConstructor
@Tag(name = "Expenses", description = "Group and personal expense management")
public class ExpenseController {

    private final ExpenseService expenseService;

    @PostMapping
    public ApiResponse<?> createExpense(@RequestBody CreateExpenseRequest request) {

        return new ApiResponse<>(
                true,
                "Expense created successfully",
                expenseService.createExpense(request)
        );
    }
    @PostMapping("/direct-split")
    public ApiResponse<?> createDirectSplit(
            @RequestBody DirectSplitRequest request) {

        expenseService.createDirectSplit(request);

        return new ApiResponse<>(true, "Direct split created", null);
    }
    @DeleteMapping("/{expenseId}")
    public ApiResponse<?> cancelExpense(@PathVariable Long expenseId) {

        expenseService.cancelExpense(expenseId);

        return new ApiResponse<>(true, "Expense cancelled", null);
    }
    @PutMapping("/{expenseId}")
    public ApiResponse<?> editExpense(
            @PathVariable Long expenseId,
            @RequestBody EditExpenseRequest request) {

        expenseService.editExpense(expenseId, request);

        return new ApiResponse<>(true, "Expense edited", null);
    }
    @GetMapping("/group/{groupId}")
    public ApiResponse<?> getGroupExpenses(@PathVariable Long groupId) {
        return new ApiResponse<>(true, "Group expenses fetched",
                expenseService.getGroupExpenses(groupId));
    }

    @GetMapping("/group/{groupId}/my")
    public ApiResponse<?> getMyExpensesInGroup(@PathVariable Long groupId) {
        return new ApiResponse<>(true, "My group expenses fetched",
                expenseService.getMyExpensesInGroup(groupId));
    }

    @GetMapping("/my")
    public ApiResponse<?> getMyExpenses() {
        return new ApiResponse<>(true, "My expenses fetched",
                expenseService.getMyExpenses());
    }

    @GetMapping("/group/{groupId}/filter")
    public ApiResponse<?> filterGroupExpenses(
            @PathVariable Long groupId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        LocalDateTime fromDate = from != null
                ? LocalDateTime.parse(from + "T00:00:00") : null;
        LocalDateTime toDate = to != null
                ? LocalDateTime.parse(to + "T23:59:59") : null;

        return new ApiResponse<>(
                true,
                "Filtered expenses fetched",
                expenseService.getFilteredGroupExpenses(groupId, category, fromDate, toDate)
        );
    }

    @GetMapping("/personal/filter")
    public ApiResponse<?> filterPersonalExpenses(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        LocalDateTime fromDate = from != null
                ? LocalDateTime.parse(from + "T00:00:00") : null;
        LocalDateTime toDate = to != null
                ? LocalDateTime.parse(to + "T23:59:59") : null;

        return new ApiResponse<>(
                true,
                "Filtered personal expenses fetched",
                expenseService.getFilteredPersonalExpenses(category, fromDate, toDate)
        );
    }

    @GetMapping("/group/{groupId}/paged")
    public ApiResponse<?> getGroupExpensesPaged(
            @PathVariable Long groupId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        LocalDateTime fromDate = from != null
                ? LocalDateTime.parse(from + "T00:00:00") : null;
        LocalDateTime toDate = to != null
                ? LocalDateTime.parse(to + "T23:59:59") : null;

        return new ApiResponse<>(
                true,
                "Expenses fetched",
                expenseService.getGroupExpensesPaged(
                        groupId, category, fromDate, toDate, page, size)
        );
    }

    @GetMapping("/personal/paged")
    public ApiResponse<?> getPersonalExpensesPaged(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        LocalDateTime fromDate = from != null
                ? LocalDateTime.parse(from + "T00:00:00") : null;
        LocalDateTime toDate = to != null
                ? LocalDateTime.parse(to + "T23:59:59") : null;

        return new ApiResponse<>(
                true,
                "Personal expenses fetched",
                expenseService.getPersonalExpensesPaged(
                        category, fromDate, toDate, page, size)
        );
    }

    @GetMapping("/group/{groupId}/search")
    public ApiResponse<?> searchGroupExpenses(
            @PathVariable Long groupId,
            @RequestParam String q) {

        if (q == null || q.isBlank()) {
            throw new ApiException("Search query cannot be empty");
        }

        return new ApiResponse<>(
                true,
                "Search results",
                expenseService.searchGroupExpenses(groupId, q)
        );
    }

    @GetMapping("/personal/search")
    public ApiResponse<?> searchPersonalExpenses(@RequestParam String q) {

        if (q == null || q.isBlank()) {
            throw new ApiException("Search query cannot be empty");
        }

        return new ApiResponse<>(
                true,
                "Search results",
                expenseService.searchPersonalExpenses(q)
        );
    }

}
