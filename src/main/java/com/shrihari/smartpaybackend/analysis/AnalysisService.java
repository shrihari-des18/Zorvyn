package com.shrihari.smartpaybackend.analysis;

import com.shrihari.smartpaybackend.ledger.LedgerEntry;
import com.shrihari.smartpaybackend.ledger.LedgerRepository;
import com.shrihari.smartpaybackend.ledger.ReferenceType;
import com.shrihari.smartpaybackend.personalexpense.PersonalExpense;
import com.shrihari.smartpaybackend.personalexpense.PersonalExpenseRepository;
import com.shrihari.smartpaybackend.user.AuthorizationService;
import com.shrihari.smartpaybackend.user.User;
import com.shrihari.smartpaybackend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final LedgerRepository ledgerRepository;
    private final PersonalExpenseRepository personalExpenseRepository;
    private final AuthorizationService authorizationService;
    private final UserRepository userRepository;

    @Cacheable(value = "analysis", key = "#root.target.getCurrentUserIdForCache()")
    public AnalysisResponse getAnalysisForCurrentUser() {

        User currentUser = authorizationService.getCurrentUser();
        Long userId = currentUser.getId();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime lastMonthStart = LocalDate.now().minusMonths(1).withDayOfMonth(1).atStartOfDay();
        LocalDateTime lastMonthEnd = monthStart;
        LocalDateTime weekStart = now.minusDays(7);

        // --- Income and Expense from ledger ---
        BigDecimal totalIncome = ledgerRepository.getTotalIncoming(userId);
        BigDecimal totalExpense = ledgerRepository.getTotalOutgoing(userId);

        if (totalIncome == null) totalIncome = BigDecimal.ZERO;
        if (totalExpense == null) totalExpense = BigDecimal.ZERO;

        BigDecimal netBalance = totalIncome.subtract(totalExpense);

        // --- This month vs last month ---
        BigDecimal thisMonth = ledgerRepository
                .sumOutgoingBetween(userId, monthStart, now);
        BigDecimal lastMonth = ledgerRepository
                .sumOutgoingBetween(userId, lastMonthStart, lastMonthEnd);

        if (thisMonth == null) thisMonth = BigDecimal.ZERO;
        if (lastMonth == null) lastMonth = BigDecimal.ZERO;

        String trend;
        BigDecimal percentageChange;

        if (lastMonth.compareTo(BigDecimal.ZERO) == 0) {
            trend = thisMonth.compareTo(BigDecimal.ZERO) > 0 ? "UP" : "STABLE";
            percentageChange = BigDecimal.ZERO;
        } else {
            BigDecimal diff = thisMonth.subtract(lastMonth);
            percentageChange = diff
                    .divide(lastMonth, 2, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).abs();
            trend = diff.compareTo(BigDecimal.ZERO) > 0 ? "UP"
                    : diff.compareTo(BigDecimal.ZERO) < 0 ? "DOWN"
                      : "STABLE";
        }

        AnalysisResponse.MonthlyComparison monthlyComparison =
                AnalysisResponse.MonthlyComparison.builder()
                        .thisMonth(thisMonth)
                        .lastMonth(lastMonth)
                        .trend(trend)
                        .percentageChange(percentageChange)
                        .build();

        // --- Weekly average ---
        BigDecimal weeklyTotal = ledgerRepository
                .sumOutgoingBetween(userId, weekStart, now);
        if (weeklyTotal == null) weeklyTotal = BigDecimal.ZERO;
        BigDecimal weeklyAverage = weeklyTotal
                .divide(BigDecimal.valueOf(7), 2, RoundingMode.HALF_UP);
        BigDecimal dailyAverage = weeklyAverage;

        // --- Category breakdown from personal expenses ---
        List<PersonalExpense> personalExpenses = personalExpenseRepository
                .findByUserId(userId);

        Map<String, List<PersonalExpense>> byCategory = personalExpenses.stream()
                .filter(e -> e.getCategory() != null)
                .collect(Collectors.groupingBy(PersonalExpense::getCategory));

        BigDecimal totalPersonalSpend = personalExpenses.stream()
                .map(PersonalExpense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<AnalysisResponse.CategoryBreakdown> categoryBreakdown = byCategory.entrySet()
                .stream()
                .map(entry -> {
                    String category = entry.getKey();
                    List<PersonalExpense> expenses = entry.getValue();

                    BigDecimal categoryTotal = expenses.stream()
                            .map(PersonalExpense::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    double percentage = totalPersonalSpend.compareTo(BigDecimal.ZERO) == 0
                            ? 0.0
                            : categoryTotal
                              .divide(totalPersonalSpend, 4, RoundingMode.HALF_UP)
                              .multiply(BigDecimal.valueOf(100))
                              .doubleValue();

                    return AnalysisResponse.CategoryBreakdown.builder()
                            .category(category)
                            .amount(categoryTotal)
                            .percentage(Math.round(percentage * 100.0) / 100.0)
                            .transactionCount(expenses.size())
                            .build();
                })
                .sorted(Comparator.comparing(
                        AnalysisResponse.CategoryBreakdown::getAmount).reversed())
                .toList();

        // --- Top and most frequent category ---
        String topSpendingCategory = categoryBreakdown.isEmpty()
                ? "N/A"
                : categoryBreakdown.get(0).getCategory();

        String mostFrequentCategory = categoryBreakdown.stream()
                .max(Comparator.comparingInt(
                        AnalysisResponse.CategoryBreakdown::getTransactionCount))
                .map(AnalysisResponse.CategoryBreakdown::getCategory)
                .orElse("N/A");

        return AnalysisResponse.builder()
                .totalIncome(totalIncome)
                .totalExpense(totalExpense)
                .netBalance(netBalance)
                .topSpendingCategory(topSpendingCategory)
                .mostFrequentCategory(mostFrequentCategory)
                .categoryBreakdown(categoryBreakdown)
                .monthlyComparison(monthlyComparison)
                .weeklyAverage(weeklyAverage)
                .dailyAverage(dailyAverage)
                .build();
    }

    public Long getCurrentUserIdForCache() {
        String identifier = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();
        return userRepository.findByEmailOrPhoneNumber(identifier, identifier)
                .map(User::getId)
                .orElse(0L);
    }
}