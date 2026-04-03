package com.shrihari.smartpaybackend.ledger;

import com.shrihari.smartpaybackend.exception.ApiException;
import com.shrihari.smartpaybackend.expense.Expense;
import com.shrihari.smartpaybackend.expense.ExpenseRepository;
import com.shrihari.smartpaybackend.group.Group;
import com.shrihari.smartpaybackend.group.GroupMember;
import com.shrihari.smartpaybackend.group.GroupMemberRepository;
import com.shrihari.smartpaybackend.ledger.dto.GroupBalanceResponse;
import com.shrihari.smartpaybackend.ledger.dto.SimplifiedDebtResponse;
import com.shrihari.smartpaybackend.ledger.dto.TransactionResponse;
import com.shrihari.smartpaybackend.ledger.dto.UserDebtSummaryResponse;
import com.shrihari.smartpaybackend.settlement.SettlementRepository;
import com.shrihari.smartpaybackend.user.AuthorizationService;
import com.shrihari.smartpaybackend.user.User;
import com.shrihari.smartpaybackend.user.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LedgerService {

    private final LedgerRepository ledgerRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final ExpenseRepository expenseRepository;
    private final SettlementRepository settlementRepository;
    private final AuthorizationService authorizationService;
    private final UserRepository userRepository;


    public void createEntry(
            Group group,
            User fromUser,
            User toUser,
            BigDecimal amount,
            ReferenceType type,
            Long referenceId) {

        LedgerEntry entry = LedgerEntry.builder()
                .group(group)
                .fromUser(fromUser)
                .toUser(toUser)
                .amount(amount)
                .referenceType(type)
                .referenceId(referenceId)
                .createdAt(LocalDateTime.now())
                .build();

        ledgerRepository.save(entry);
    }

    public BigDecimal getNetBalance(Long groupId, Long userId) {

        BigDecimal incoming = ledgerRepository.sumIncoming(groupId, userId);
        BigDecimal outgoing = ledgerRepository.sumOutgoing(groupId, userId);

        return incoming.subtract(outgoing);
    }

    public BigDecimal getUserBalance(Long groupId, Long userId) {
        return getNetBalance(groupId, userId);
    }

    public java.util.List<GroupBalanceResponse> getGroupBalances(Long groupId) {

        var members = groupMemberRepository.findByGroupId(groupId);

        return members.stream()
                .map(member -> {

                    Long userId = member.getUser().getId();

                    BigDecimal incoming =
                            ledgerRepository.sumIncoming(groupId, userId);

                    BigDecimal outgoing =
                            ledgerRepository.sumOutgoing(groupId, userId);

                    BigDecimal net = incoming.subtract(outgoing);

                    return GroupBalanceResponse.builder()
                            .userId(userId)
                            .fullName(member.getUser().getFullName())
                            .netBalance(net)
                            .build();
                })
                .toList();
    }
    @Transactional
    public List<TransactionResponse> getGroupTransactions(Long groupId) {

        authorizationService.validateGroupMember(groupId);

        User currentUser = authorizationService.getCurrentUser();

        List<LedgerEntry> entries = ledgerRepository.findByGroup_Id(groupId);

        // Group EXPENSE entries by referenceId
        Map<Long, List<LedgerEntry>> expenseGroups = entries.stream()
                .filter(e -> e.getReferenceType() == ReferenceType.EXPENSE)
                .collect(Collectors.groupingBy(LedgerEntry::getReferenceId));

        List<TransactionResponse> result = new ArrayList<>();

        for (Map.Entry<Long, List<LedgerEntry>> group : expenseGroups.entrySet()) {

            Long expenseId = group.getKey();
            List<LedgerEntry> expenseEntries = group.getValue();

            Expense expense = expenseRepository.findById(expenseId).orElse(null);
            if (expense == null) continue;

            boolean iPaid = expense.getPaidBy().getId().equals(currentUser.getId());

            if (iPaid) {
                result.add(TransactionResponse.builder()
                        .type("EXPENSE")
                        .amount(expense.getTotalAmount())
                        .description(expense.getDescription())
                        .createdAt(expense.getCreatedAt())
                        .fromUserId(currentUser.getId())
                        .fromUserName(currentUser.getFullName())
                        .toUserId(currentUser.getId())
                        .toUserName(currentUser.getFullName())
                        .perspective("SPENT")
                        .build());
            } else {
                expenseEntries.stream()
                        .filter(e -> e.getFromUser().getId().equals(currentUser.getId()))
                        .findFirst()
                        .ifPresentOrElse(
                                e -> result.add(TransactionResponse.builder()
                                        .type("EXPENSE")
                                        .amount(e.getAmount())
                                        .description(expense.getDescription())
                                        .createdAt(expense.getCreatedAt())
                                        .fromUserId(currentUser.getId())
                                        .fromUserName(currentUser.getFullName())
                                        .toUserId(e.getToUser().getId())
                                        .toUserName(e.getToUser().getFullName())
                                        .perspective("OWED")
                                        .build()),
                                // Current user is in group but not involved in this expense
                                () -> result.add(TransactionResponse.builder()
                                        .type("EXPENSE")
                                        .amount(expense.getTotalAmount())
                                        .description(expense.getDescription())
                                        .createdAt(expense.getCreatedAt())
                                        .fromUserId(expense.getPaidBy().getId())
                                        .fromUserName(expense.getPaidBy().getFullName())
                                        .toUserId(expense.getPaidBy().getId())
                                        .toUserName(expense.getPaidBy().getFullName())
                                        .perspective("OTHER")
                                        .build())
                        );
            }
        }

        // Handle SETTLEMENT entries
        entries.stream()
                .filter(e -> e.getReferenceType() == ReferenceType.SETTLEMENT)
                .forEach(e -> {
                    boolean iReceived = e.getToUser().getId().equals(currentUser.getId());
                    result.add(TransactionResponse.builder()
                            .type("SETTLEMENT")
                            .amount(e.getAmount())
                            .description("Settlement")
                            .createdAt(e.getCreatedAt())
                            .fromUserId(e.getFromUser().getId())
                            .fromUserName(e.getFromUser().getFullName())
                            .toUserId(e.getToUser().getId())
                            .toUserName(e.getToUser().getFullName())
                            .perspective(iReceived ? "RECEIVED" : "PAID")
                            .build());
                });

        result.sort(Comparator.comparing(TransactionResponse::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));

        return result;
    }
    public List<SimplifiedDebtResponse> getSimplifiedDebts(Long groupId) {
        authorizationService.validateGroupMember(groupId);
        //  Get balances
        Map<User, BigDecimal> balanceMap = getBalanceMap(groupId);

        // Separate creditors and debtors
        List<Map.Entry<User, BigDecimal>> creditors = new ArrayList<>();
        List<Map.Entry<User, BigDecimal>> debtors = new ArrayList<>();

        for (Map.Entry<User, BigDecimal> entry : balanceMap.entrySet()) {

            if (entry.getValue().compareTo(BigDecimal.ZERO) > 0) {
                creditors.add(entry);
            } else if (entry.getValue().compareTo(BigDecimal.ZERO) < 0) {
                debtors.add(entry);
            }
        }

        //  Sort
        creditors.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        debtors.sort((a, b) -> a.getValue().compareTo(b.getValue())); // more negative first

        List<SimplifiedDebtResponse> result = new ArrayList<>();

        int i = 0; // debtor pointer
        int j = 0; // creditor pointer

        while (i < debtors.size() && j < creditors.size()) {

            User debtor = debtors.get(i).getKey();
            User creditor = creditors.get(j).getKey();

            BigDecimal debtAmount = debtors.get(i).getValue().abs();
            BigDecimal creditAmount = creditors.get(j).getValue();

            BigDecimal settledAmount = debtAmount.min(creditAmount);

            result.add(
                    SimplifiedDebtResponse.builder()
                            .fromUserId(debtor.getId())
                            .fromUserName(debtor.getFullName())
                            .toUserId(creditor.getId())
                            .toUserName(creditor.getFullName())
                            .amount(settledAmount)
                            .build()
            );

            // update values
            debtors.get(i).setValue(
                    debtors.get(i).getValue().add(settledAmount)
            );

            creditors.get(j).setValue(
                    creditors.get(j).getValue().subtract(settledAmount)
            );

            if (debtors.get(i).getValue().compareTo(BigDecimal.ZERO) == 0) {
                i++;
            }

            if (creditors.get(j).getValue().compareTo(BigDecimal.ZERO) == 0) {
                j++;
            }
        }

        return result;
    }
    private Map<User, BigDecimal> getBalanceMap(Long groupId) {
        authorizationService.validateGroupMember(groupId);
        List<GroupMember> members =
                groupMemberRepository.findByGroupId(groupId);

        Map<User, BigDecimal> balanceMap = new HashMap<>();

        for (GroupMember member : members) {

            User user = member.getUser();

            BigDecimal incoming =
                    ledgerRepository.sumIncoming(groupId, user.getId());

            BigDecimal outgoing =
                    ledgerRepository.sumOutgoing(groupId, user.getId());

            if (incoming == null) incoming = BigDecimal.ZERO;
            if (outgoing == null) outgoing = BigDecimal.ZERO;

            balanceMap.put(user, incoming.subtract(outgoing));
        }

        return balanceMap;
    }

    @Transactional
    public List<TransactionResponse> getMyTransactions() {

        User currentUser = authorizationService.getCurrentUser();

        List<LedgerEntry> entries = ledgerRepository
                .findByFromUser_IdOrToUser_Id(
                        currentUser.getId(),
                        currentUser.getId()
                );
        // Group EXPENSE entries by referenceId to avoid duplicate splits
        Map<Long, List<LedgerEntry>> expenseGroups = entries.stream()
                .filter(e -> e.getReferenceType() == ReferenceType.EXPENSE)
                .collect(Collectors.groupingBy(LedgerEntry::getReferenceId));

        List<TransactionResponse> result = new ArrayList<>();

        for (Map.Entry<Long, List<LedgerEntry>> group : expenseGroups.entrySet()) {

            Long expenseId = group.getKey();
            List<LedgerEntry> expenseEntries = group.getValue();

            Expense expense = expenseRepository.findById(expenseId).orElse(null);
            if (expense == null) continue;

            boolean iPaid = expense.getPaidBy().getId().equals(currentUser.getId());

            if (iPaid) {
                // Show full expense amount — I paid for everyone
                result.add(TransactionResponse.builder()
                        .type("EXPENSE")
                        .amount(expense.getTotalAmount())
                        .description(expense.getDescription())
                        .createdAt(expense.getCreatedAt())
                        .fromUserId(currentUser.getId())
                        .fromUserName(currentUser.getFullName())
                        .toUserId(currentUser.getId())
                        .toUserName(currentUser.getFullName())
                        .perspective("SPENT")
                        .build());
            } else {
                // I am a debtor — show only my split amount
                expenseEntries.stream()
                        .filter(e -> e.getFromUser().getId().equals(currentUser.getId()))
                        .findFirst()
                        .ifPresent(e -> result.add(TransactionResponse.builder()
                                .type("EXPENSE")
                                .amount(e.getAmount())
                                .description(expense.getDescription())
                                .createdAt(expense.getCreatedAt())
                                .fromUserId(currentUser.getId())
                                .fromUserName(currentUser.getFullName())
                                .toUserId(e.getToUser().getId())
                                .toUserName(e.getToUser().getFullName())
                                .perspective("OWED")
                                .build()));
            }
        }
        // Handle SETTLEMENT entries separately
        entries.stream()
                .filter(e -> e.getReferenceType() == ReferenceType.SETTLEMENT)
                .forEach(e -> {
                    boolean iReceived = e.getToUser().getId().equals(currentUser.getId());
                    result.add(TransactionResponse.builder()
                            .type("SETTLEMENT")
                            .amount(e.getAmount())
                            .description("Settlement")
                            .createdAt(e.getCreatedAt())
                            .fromUserId(e.getFromUser().getId())
                            .fromUserName(e.getFromUser().getFullName())
                            .toUserId(e.getToUser().getId())
                            .toUserName(e.getToUser().getFullName())
                            .perspective(iReceived ? "RECEIVED" : "PAID")
                            .build());
                });
        // Handle PERSONAL_EXPENSE entries
        entries.stream()
                .filter(e -> e.getReferenceType() == ReferenceType.PERSONAL_EXPENSE)
                .forEach(e -> result.add(TransactionResponse.builder()
                        .type("PERSONAL_EXPENSE")
                        .amount(e.getAmount())
                        .description("Personal Expense")
                        .createdAt(e.getCreatedAt())
                        .fromUserId(currentUser.getId())
                        .fromUserName(currentUser.getFullName())
                        .toUserId(currentUser.getId())
                        .toUserName(currentUser.getFullName())
                        .perspective("SPENT")
                        .build()));
        result.sort(Comparator.comparing(TransactionResponse::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }
    public List<UserDebtSummaryResponse> getWhoOwesMe() {

        User currentUser = authorizationService.getCurrentUser();

        List<Object[]> rows =
                ledgerRepository.findWhoOwesMe(currentUser.getId());

        return rows.stream()
                .map(row ->
                        UserDebtSummaryResponse.builder()
                                .userId((Long) row[0])
                                .userName((String) row[1])
                                .amount((BigDecimal) row[2])
                                .build()
                )
                .toList();
    }
    public List<UserDebtSummaryResponse> getWhomIOwe() {

        User currentUser = authorizationService.getCurrentUser();

        List<Object[]> rows =
                ledgerRepository.findWhomIOwe(currentUser.getId());

        return rows.stream()
                .map(row ->
                        UserDebtSummaryResponse.builder()
                                .userId((Long) row[0])
                                .userName((String) row[1])
                                .amount((BigDecimal) row[2])
                                .build()
                )
                .toList();
    }

    public List<UserDebtSummaryResponse> getWhoOwesMe(Long userId) {
        List<Object[]> rows = ledgerRepository.findWhoOwesMe(userId);
        return rows.stream()
                .map(row -> UserDebtSummaryResponse.builder()
                        .userId((Long) row[0])
                        .userName((String) row[1])
                        .amount((BigDecimal) row[2])
                        .build()
                )
                .toList();
    }

    public List<UserDebtSummaryResponse> getWhomIOwe(Long userId) {
        List<Object[]> rows = ledgerRepository.findWhomIOwe(userId);
        return rows.stream()
                .map(row -> UserDebtSummaryResponse.builder()
                        .userId((Long) row[0])
                        .userName((String) row[1])
                        .amount((BigDecimal) row[2])
                        .build()
                )
                .toList();
    }

    public List<TransactionResponse> getMyTransactions(Long userId) {

        User currentUser = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.FORBIDDEN));

        List<LedgerEntry> entries = ledgerRepository
                .findByFromUser_IdOrToUser_Id(userId, userId);

        Map<Long, List<LedgerEntry>> expenseGroups = entries.stream()
                .filter(e -> e.getReferenceType() == ReferenceType.EXPENSE)
                .collect(Collectors.groupingBy(LedgerEntry::getReferenceId));

        List<TransactionResponse> result = new ArrayList<>();

        for (Map.Entry<Long, List<LedgerEntry>> group : expenseGroups.entrySet()) {

            Long expenseId = group.getKey();
            List<LedgerEntry> expenseEntries = group.getValue();

            Expense expense = expenseRepository.findById(expenseId).orElse(null);
            if (expense == null) continue;

            boolean iPaid = expense.getPaidBy().getId().equals(userId);

            if (iPaid) {
                result.add(TransactionResponse.builder()
                        .type("EXPENSE")
                        .amount(expense.getTotalAmount())
                        .description(expense.getDescription())
                        .createdAt(expense.getCreatedAt())
                        .fromUserId(userId)
                        .fromUserName(currentUser.getFullName())
                        .toUserId(userId)
                        .toUserName(currentUser.getFullName())
                        .perspective("SPENT")
                        .build());
            } else {
                expenseEntries.stream()
                        .filter(e -> e.getFromUser().getId().equals(userId))
                        .findFirst()
                        .ifPresent(e -> result.add(TransactionResponse.builder()
                                .type("EXPENSE")
                                .amount(e.getAmount())
                                .description(expense.getDescription())
                                .createdAt(expense.getCreatedAt())
                                .fromUserId(userId)
                                .fromUserName(currentUser.getFullName())
                                .toUserId(e.getToUser().getId())
                                .toUserName(e.getToUser().getFullName())
                                .perspective("OWED")
                                .build()));
            }
        }

        entries.stream()
                .filter(e -> e.getReferenceType() == ReferenceType.SETTLEMENT)
                .forEach(e -> {
                    boolean iReceived = e.getToUser().getId().equals(userId);
                    result.add(TransactionResponse.builder()
                            .type("SETTLEMENT")
                            .amount(e.getAmount())
                            .description("Settlement")
                            .createdAt(e.getCreatedAt())
                            .fromUserId(e.getFromUser().getId())
                            .fromUserName(e.getFromUser().getFullName())
                            .toUserId(e.getToUser().getId())
                            .toUserName(e.getToUser().getFullName())
                            .perspective(iReceived ? "RECEIVED" : "PAID")
                            .build());
                });

        entries.stream()
                .filter(e -> e.getReferenceType() == ReferenceType.PERSONAL_EXPENSE)
                .forEach(e -> result.add(TransactionResponse.builder()
                        .type("PERSONAL_EXPENSE")
                        .amount(e.getAmount())
                        .description("Personal Expense")
                        .createdAt(e.getCreatedAt())
                        .fromUserId(userId)
                        .fromUserName(currentUser.getFullName())
                        .toUserId(userId)
                        .toUserName(currentUser.getFullName())
                        .perspective("SPENT")
                        .build()));

        result.sort(Comparator.comparing(TransactionResponse::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));

        return result;
    }

    public List<TransactionResponse> getGroupTransactions(Long groupId, Long userId) {

        User currentUser = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.FORBIDDEN));

        List<LedgerEntry> entries = ledgerRepository.findByGroup_Id(groupId);

        Map<Long, List<LedgerEntry>> expenseGroups = entries.stream()
                .filter(e -> e.getReferenceType() == ReferenceType.EXPENSE)
                .collect(Collectors.groupingBy(LedgerEntry::getReferenceId));

        List<TransactionResponse> result = new ArrayList<>();

        for (Map.Entry<Long, List<LedgerEntry>> group : expenseGroups.entrySet()) {

            Long expenseId = group.getKey();
            List<LedgerEntry> expenseEntries = group.getValue();

            Expense expense = expenseRepository.findById(expenseId).orElse(null);
            if (expense == null) continue;

            boolean iPaid = expense.getPaidBy().getId().equals(userId);

            if (iPaid) {
                result.add(TransactionResponse.builder()
                        .type("EXPENSE")
                        .amount(expense.getTotalAmount())
                        .description(expense.getDescription())
                        .createdAt(expense.getCreatedAt())
                        .fromUserId(userId)
                        .fromUserName(currentUser.getFullName())
                        .toUserId(userId)
                        .toUserName(currentUser.getFullName())
                        .perspective("SPENT")
                        .build());
            } else {
                expenseEntries.stream()
                        .filter(e -> e.getFromUser().getId().equals(userId))
                        .findFirst()
                        .ifPresentOrElse(
                                e -> result.add(TransactionResponse.builder()
                                        .type("EXPENSE")
                                        .amount(e.getAmount())
                                        .description(expense.getDescription())
                                        .createdAt(expense.getCreatedAt())
                                        .fromUserId(userId)
                                        .fromUserName(currentUser.getFullName())
                                        .toUserId(e.getToUser().getId())
                                        .toUserName(e.getToUser().getFullName())
                                        .perspective("OWED")
                                        .build()),
                                () -> result.add(TransactionResponse.builder()
                                        .type("EXPENSE")
                                        .amount(expense.getTotalAmount())
                                        .description(expense.getDescription())
                                        .createdAt(expense.getCreatedAt())
                                        .fromUserId(expense.getPaidBy().getId())
                                        .fromUserName(expense.getPaidBy().getFullName())
                                        .toUserId(expense.getPaidBy().getId())
                                        .toUserName(expense.getPaidBy().getFullName())
                                        .perspective("OTHER")
                                        .build())
                        );
            }
        }

        entries.stream()
                .filter(e -> e.getReferenceType() == ReferenceType.SETTLEMENT)
                .forEach(e -> {
                    boolean iReceived = e.getToUser().getId().equals(userId);
                    result.add(TransactionResponse.builder()
                            .type("SETTLEMENT")
                            .amount(e.getAmount())
                            .description("Settlement")
                            .createdAt(e.getCreatedAt())
                            .fromUserId(e.getFromUser().getId())
                            .fromUserName(e.getFromUser().getFullName())
                            .toUserId(e.getToUser().getId())
                            .toUserName(e.getToUser().getFullName())
                            .perspective(iReceived ? "RECEIVED" : "PAID")
                            .build());
                });

        result.sort(Comparator.comparing(TransactionResponse::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));

        return result;
    }

    public List<GroupBalanceResponse> getGroupBalances(Long groupId, Long userId) {
        // userId param unused here but keeps signature consistent
        var members = groupMemberRepository.findByGroupId(groupId);

        return members.stream()
                .map(member -> {
                    Long memberId = member.getUser().getId();
                    BigDecimal incoming = ledgerRepository.sumIncoming(groupId, memberId);
                    BigDecimal outgoing = ledgerRepository.sumOutgoing(groupId, memberId);
                    BigDecimal net = incoming.subtract(outgoing);

                    return GroupBalanceResponse.builder()
                            .userId(memberId)
                            .fullName(member.getUser().getFullName())
                            .netBalance(net)
                            .build();
                })
                .toList();
    }

}
