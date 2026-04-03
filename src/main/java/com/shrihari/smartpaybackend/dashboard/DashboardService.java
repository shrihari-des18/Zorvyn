package com.shrihari.smartpaybackend.dashboard;

import com.shrihari.smartpaybackend.friend.FriendshipRepository;
import com.shrihari.smartpaybackend.group.GroupMemberRepository;
import com.shrihari.smartpaybackend.ledger.LedgerRepository;
import com.shrihari.smartpaybackend.ledger.LedgerService;
import com.shrihari.smartpaybackend.ledger.dto.TransactionResponse;
import com.shrihari.smartpaybackend.ledger.dto.UserDebtSummaryResponse;
import com.shrihari.smartpaybackend.user.User;
import com.shrihari.smartpaybackend.user.UserRepository;
import com.shrihari.smartpaybackend.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.concurrent.CompletableFuture;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final LedgerRepository ledgerRepository;
    private final LedgerService ledgerService;
    private final UserRepository userRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final FriendshipRepository friendshipRepository;

    private User getCurrentUser() {
        String identifier = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();
        return userRepository
                .findByEmailOrPhoneNumber(identifier, identifier)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.FORBIDDEN));
    }

    public DashboardSummaryResponse getSummary() {

        User currentUser = getCurrentUser();

        BigDecimal incoming = ledgerRepository.getTotalIncoming(currentUser.getId());
        BigDecimal outgoing = ledgerRepository.getTotalOutgoing(currentUser.getId());
        BigDecimal totalBalance = incoming.subtract(outgoing);

        return DashboardSummaryResponse.builder()
                .totalBalance(totalBalance)
                .build();
    }

    public List<Map<String, Object>> getWeeklySummary() {

        User currentUser = getCurrentUser();
        List<Map<String, Object>> result = new ArrayList<>();

        for (int i = 6; i >= 0; i--) {

            LocalDateTime dayStart = LocalDate.now().minusDays(i).atStartOfDay();
            LocalDateTime dayEnd = dayStart.plusDays(1);

            BigDecimal expense = ledgerRepository
                    .sumOutgoingBetween(currentUser.getId(), dayStart, dayEnd);
            BigDecimal income = ledgerRepository
                    .sumIncomingBetween(currentUser.getId(), dayStart, dayEnd);

            Map<String, Object> day = new LinkedHashMap<>();
            day.put("day", dayStart.getDayOfWeek()
                    .getDisplayName(TextStyle.FULL, Locale.ENGLISH));
            day.put("date", dayStart.toLocalDate().toString());
            day.put("expense", expense != null ? expense : BigDecimal.ZERO);
            day.put("income", income != null ? income : BigDecimal.ZERO);

            result.add(day);
        }

        return result;
    }

    @Cacheable(value = "weeklyChart", key = "#root.target.getCurrentUserIdForCache()")
    public DashboardChartResponse getChart() {

        User currentUser = getCurrentUser();

        List<String> labels = new ArrayList<>();
        List<BigDecimal> incomeList = new ArrayList<>();
        List<BigDecimal> expenseList = new ArrayList<>();

        for (int i = 6; i >= 0; i--) {

            LocalDateTime dayStart = LocalDate.now().minusDays(i).atStartOfDay();
            LocalDateTime dayEnd = dayStart.plusDays(1);

            String label = dayStart.getDayOfWeek()
                    .getDisplayName(TextStyle.SHORT, Locale.ENGLISH);

            BigDecimal expense = ledgerRepository
                    .sumOutgoingBetween(currentUser.getId(), dayStart, dayEnd);
            BigDecimal income = ledgerRepository
                    .sumIncomingBetween(currentUser.getId(), dayStart, dayEnd);

            labels.add(label);
            expenseList.add(expense != null ? expense : BigDecimal.ZERO);
            incomeList.add(income != null ? income : BigDecimal.ZERO);
        }

        return DashboardChartResponse.builder()
                .labels(labels)
                .income(incomeList)
                .expense(expenseList)
                .build();
    }

    @Cacheable(value = "dashboardSummary", key = "#root.target.getCurrentUserIdForCache()")
    public DashboardSummaryDetailResponse getSummaryDetail() {

        User currentUser = getCurrentUser();
        Long userId = currentUser.getId();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime lastMonthStart = LocalDate.now().minusMonths(1).withDayOfMonth(1).atStartOfDay();
        LocalDateTime lastMonthEnd = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        // --- Fire all DB queries in parallel ---
        CompletableFuture<BigDecimal> incomingFuture = CompletableFuture
                .supplyAsync(() -> ledgerRepository.getTotalIncoming(userId));

        CompletableFuture<BigDecimal> outgoingFuture = CompletableFuture
                .supplyAsync(() -> ledgerRepository.getTotalOutgoing(userId));

        CompletableFuture<BigDecimal> monthlySpentFuture = CompletableFuture
                .supplyAsync(() -> ledgerRepository.sumOutgoingBetween(userId, monthStart, now));

        CompletableFuture<BigDecimal> lastMonthSpentFuture = CompletableFuture
                .supplyAsync(() -> ledgerRepository.sumOutgoingBetween(userId, lastMonthStart, lastMonthEnd));

        CompletableFuture<List<UserDebtSummaryResponse>> whoOwesMeFuture = CompletableFuture
                .supplyAsync(() -> ledgerService.getWhoOwesMe(userId));

        CompletableFuture<List<UserDebtSummaryResponse>> whomIOweFuture = CompletableFuture
                .supplyAsync(() -> ledgerService.getWhomIOwe(userId));

        CompletableFuture<List<TransactionResponse>> transactionsFuture = CompletableFuture
                .supplyAsync(() -> ledgerService.getMyTransactions(userId));

        CompletableFuture<Integer> groupCountFuture = CompletableFuture
                .supplyAsync(() -> groupMemberRepository
                        .findAllWithGroupAndCreator(userId).size());

        CompletableFuture<Integer> friendCountFuture = CompletableFuture
                .supplyAsync(() -> (int) friendshipRepository
                        .findByRequesterOrReceiverAndStatus(
                                currentUser,
                                currentUser,
                                com.shrihari.smartpaybackend.friend.FriendshipStatus.ACCEPTED
                        ).size());

        // --- Wait for all to complete ---
        CompletableFuture.allOf(
                incomingFuture, outgoingFuture, monthlySpentFuture,
                lastMonthSpentFuture, whoOwesMeFuture, whomIOweFuture,
                transactionsFuture, groupCountFuture, friendCountFuture
        ).join();

        // --- Unwrap results ---
        BigDecimal incoming = incomingFuture.join();
        BigDecimal outgoing = outgoingFuture.join();
        BigDecimal monthlySpent = monthlySpentFuture.join();
        BigDecimal lastMonthSpent = lastMonthSpentFuture.join();
        List<UserDebtSummaryResponse> whoOwesMe = whoOwesMeFuture.join();
        List<UserDebtSummaryResponse> whomIOwe = whomIOweFuture.join();
        List<TransactionResponse> transactions = transactionsFuture.join();
        int groupCount = groupCountFuture.join();
        int friendCount = friendCountFuture.join();

        // --- Null safety ---
        if (incoming == null) incoming = BigDecimal.ZERO;
        if (outgoing == null) outgoing = BigDecimal.ZERO;
        if (monthlySpent == null) monthlySpent = BigDecimal.ZERO;
        if (lastMonthSpent == null) lastMonthSpent = BigDecimal.ZERO;

        // --- Balance ---
        BigDecimal total = incoming.subtract(outgoing);

        DashboardSummaryDetailResponse.Balance balance =
                DashboardSummaryDetailResponse.Balance.builder()
                        .total(total)
                        .changePercent(null)
                        .monthlySpent(monthlySpent)
                        .monthlyLimit(null)
                        .build();

        // --- Pending Requests ---
        List<DashboardSummaryDetailResponse.PendingRequest> pendingRequests = new ArrayList<>();

        for (UserDebtSummaryResponse debt : whoOwesMe) {
            pendingRequests.add(
                    DashboardSummaryDetailResponse.PendingRequest.builder()
                            .id(debt.getUserId())
                            .name(debt.getUserName())
                            .description("Owes you money")
                            .amount(debt.getAmount())
                            .type("OWES_YOU")
                            .dueDate(null)
                            .build()
            );
        }

        for (UserDebtSummaryResponse debt : whomIOwe) {
            pendingRequests.add(
                    DashboardSummaryDetailResponse.PendingRequest.builder()
                            .id(debt.getUserId())
                            .name(debt.getUserName())
                            .description("You owe money")
                            .amount(debt.getAmount())
                            .type("YOU_OWE")
                            .dueDate(null)
                            .build()
            );
        }

        // --- Recent Activity ---
        AtomicLong fakeId = new AtomicLong(1);
        List<DashboardSummaryDetailResponse.RecentActivity> recentActivity =
                transactions.stream()
                        .limit(5)
                        .map(tx -> DashboardSummaryDetailResponse.RecentActivity.builder()
                                .id(fakeId.getAndIncrement())
                                .description(tx.getDescription())
                                .amount(tx.getAmount())
                                .type(tx.getType())
                                .createdAt(tx.getCreatedAt())
                                .fromUserName(tx.getFromUserName())
                                .toUserName(tx.getToUserName())
                                .build()
                        )
                        .toList();

        // --- Summary ---
        BigDecimal totalOwes = whomIOwe.stream()
                .map(UserDebtSummaryResponse::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalIsOwed = whoOwesMe.stream()
                .map(UserDebtSummaryResponse::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        DashboardSummaryDetailResponse.Summary summary =
                DashboardSummaryDetailResponse.Summary.builder()
                        .totalBalance(total)
                        .totalOwes(totalOwes)
                        .totalIsOwed(totalIsOwed)
                        .groupCount(groupCount)
                        .friendCount(friendCount)
                        .build();

        // --- Expense Breakdown ---
        String trend;
        BigDecimal percentageChange;

        if (lastMonthSpent.compareTo(BigDecimal.ZERO) == 0) {
            trend = monthlySpent.compareTo(BigDecimal.ZERO) > 0 ? "UP" : "STABLE";
            percentageChange = BigDecimal.ZERO;
        } else {
            BigDecimal diff = monthlySpent.subtract(lastMonthSpent);
            percentageChange = diff
                    .divide(lastMonthSpent, 2, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            trend = diff.compareTo(BigDecimal.ZERO) > 0 ? "UP"
                    : diff.compareTo(BigDecimal.ZERO) < 0 ? "DOWN"
                    : "STABLE";
        }

        DashboardSummaryDetailResponse.ExpenseBreakdown expenseBreakdown =
                DashboardSummaryDetailResponse.ExpenseBreakdown.builder()
                        .thisMonth(monthlySpent)
                        .lastMonth(lastMonthSpent)
                        .trend(trend)
                        .percentageChange(percentageChange.abs())
                        .build();

        return DashboardSummaryDetailResponse.builder()
                .balance(balance)
                .pendingRequests(pendingRequests)
                .recentActivity(recentActivity)
                .summary(summary)
                .expenseBreakdown(expenseBreakdown)
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