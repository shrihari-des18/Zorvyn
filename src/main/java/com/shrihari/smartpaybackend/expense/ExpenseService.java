package com.shrihari.smartpaybackend.expense;

import com.shrihari.smartpaybackend.exception.ApiException;
import com.shrihari.smartpaybackend.expense.dto.*;
import com.shrihari.smartpaybackend.group.*;
import com.shrihari.smartpaybackend.ledger.LedgerEntry;
import com.shrihari.smartpaybackend.ledger.LedgerRepository;
import com.shrihari.smartpaybackend.ledger.LedgerService;
import com.shrihari.smartpaybackend.ledger.ReferenceType;
import com.shrihari.smartpaybackend.personalexpense.PersonalExpense;
import com.shrihari.smartpaybackend.personalexpense.PersonalExpenseRepository;
import com.shrihari.smartpaybackend.user.AuthorizationService;
import com.shrihari.smartpaybackend.user.User;
import com.shrihari.smartpaybackend.user.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.shrihari.smartpaybackend.common.PagedResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseSplitRepository splitRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final LedgerService ledgerService;
    private final LedgerRepository ledgerRepository;
    private final AuthorizationService authorizationService;
    private final PersonalExpenseRepository personalExpenseRepository;

    @Transactional
    @CacheEvict(value = {"groupDetail", "dashboardSummary", "weeklyChart"}, allEntries = true)
    public ExpenseResponse createExpense(CreateExpenseRequest request) {
        authorizationService.validateGroupMember(request.getGroupId());

        String identifier = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        User payer = userRepository
                .findByEmailOrPhoneNumber(identifier, identifier)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.FORBIDDEN));

        Group group = groupRepository.findById(request.getGroupId())
                .orElseThrow(() -> new ApiException("Group not found", HttpStatus.FORBIDDEN));

        if (request.getAmount() == null ||
                request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException("Amount must be positive", HttpStatus.FORBIDDEN);
        }

        Expense expense = Expense.builder()
                .group(group)
                .paidBy(payer)
                .description(request.getDescription())
                .totalAmount(request.getAmount())
                .createdAt(LocalDateTime.now())
                .build();

        expenseRepository.save(expense);

        List<ExpenseResponse.SplitResponse> splitResponses = new ArrayList<>();

        // 🔹 EQUAL SPLIT
        if (request.getSplitType() == SplitType.EQUAL) {

            if (request.getUserIds() == null || request.getUserIds().isEmpty()) {
                throw new ApiException("Users required for equal split", HttpStatus.FORBIDDEN);
            }

            BigDecimal splitAmount =
                    request.getAmount().divide(
                            BigDecimal.valueOf(request.getUserIds().size()),
                            2,
                            RoundingMode.HALF_UP
                    );

            for (Long userId : request.getUserIds()) {

                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new ApiException("User not found", HttpStatus.FORBIDDEN));

                groupMemberRepository
                        .findByGroup_IdAndUser_Id(group.getId(), user.getId())
                        .orElseThrow(() -> new ApiException("User not in group", HttpStatus.FORBIDDEN));

                splitRepository.save(
                        ExpenseSplit.builder()
                                .expense(expense)
                                .user(user)
                                .amountOwed(splitAmount)
                                .build()
                );

                if (!user.getId().equals(payer.getId())) {
                    ledgerService.createEntry(
                            group,
                            user,
                            payer,
                            splitAmount,
                            ReferenceType.EXPENSE,
                            expense.getId()
                    );
                }

                splitResponses.add(
                        ExpenseResponse.SplitResponse.builder()
                                .userId(user.getId())
                                .amountOwed(splitAmount)
                                .build()
                );
            }
        }

        // 🔹 EXACT SPLIT
        if (request.getSplitType() == SplitType.EXACT) {

            if (request.getExactSplits() == null ||
                    request.getExactSplits().isEmpty()) {
                throw new ApiException("Exact splits required", HttpStatus.FORBIDDEN);
            }

            BigDecimal total = BigDecimal.ZERO;

            for (CreateExpenseRequest.ExactSplit split : request.getExactSplits()) {
                total = total.add(split.getAmount());
            }

            if (total.compareTo(request.getAmount()) != 0) {
                throw new ApiException("Exact split total mismatch", HttpStatus.FORBIDDEN);
            }

            for (CreateExpenseRequest.ExactSplit split : request.getExactSplits()) {

                User user = userRepository.findById(split.getUserId())
                        .orElseThrow(() -> new ApiException("User not found", HttpStatus.FORBIDDEN));

                groupMemberRepository
                        .findByGroup_IdAndUser_Id(group.getId(), user.getId())
                        .orElseThrow(() -> new ApiException("User not in group", HttpStatus.FORBIDDEN));

                splitRepository.save(
                        ExpenseSplit.builder()
                                .expense(expense)
                                .user(user)
                                .amountOwed(split.getAmount())
                                .build()
                );

                if (!user.getId().equals(payer.getId())) {
                    ledgerService.createEntry(
                            group,
                            user,
                            payer,
                            split.getAmount(),
                            ReferenceType.EXPENSE,
                            expense.getId()
                    );
                }

                splitResponses.add(
                        ExpenseResponse.SplitResponse.builder()
                                .userId(user.getId())
                                .amountOwed(split.getAmount())
                                .build()
                );
            }
        }

        return ExpenseResponse.builder()
                .id(expense.getId())
                .description(expense.getDescription())
                .totalAmount(expense.getTotalAmount())
                .paidBy(payer.getFullName())
                .createdAt(expense.getCreatedAt())
                .splits(splitResponses)
                .build();
    }

    @Transactional
    public void createDirectSplit(DirectSplitRequest request) {

        Group group = Group.builder()
                .name("Direct Split")
                .description("Temporary split")
                .groupCode(generateGroupCode())
                .isTemporary(true)
                .build();


        groupRepository.save(group);

        List<Long> memberIds;

        if (request.getSplitType() == SplitType.EQUAL) {
            memberIds = request.getUserIds();
        } else {
            memberIds = request.getExactSplits()
                    .stream()
                    .map(DirectSplitRequest.ExactSplit::getUserId)
                    .toList();
        }

        for (Long userId : memberIds) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ApiException("User not found", HttpStatus.FORBIDDEN));

            groupMemberRepository.save(
                    GroupMember.builder()
                            .group(group)
                            .user(user)
                            .role(GroupRole.MEMBER)
                            .build()
            );
        }

        createExpenseFromSplit(group, request);
    }

    private void createExpenseFromSplit(Group group,
                                        DirectSplitRequest request) {

        User payer = userRepository.findById(request.getPayerId())
                .orElseThrow(() -> new ApiException("Payer not found", HttpStatus.FORBIDDEN));

        Expense expense = Expense.builder()
                .group(group)
                .paidBy(payer)
                .description(request.getDescription())
                .totalAmount(request.getAmount())
                .createdAt(LocalDateTime.now())
                .build();

        expenseRepository.save(expense);

        if (request.getSplitType() == SplitType.EQUAL) {

            int size = request.getUserIds().size();
            BigDecimal splitAmount =
                    request.getAmount().divide(BigDecimal.valueOf(size));

            for (Long userId : request.getUserIds()) {

                if (!userId.equals(payer.getId())) {

                    User user = userRepository.findById(userId)
                            .orElseThrow();

                    ledgerService.createEntry(
                            group,
                            user,
                            payer,
                            splitAmount,
                            ReferenceType.EXPENSE,
                            expense.getId()
                    );
                }
            }
        }

        if (request.getSplitType() == SplitType.EXACT) {

            BigDecimal total = BigDecimal.ZERO;

            for (DirectSplitRequest.ExactSplit split : request.getExactSplits()) {
                total = total.add(split.getAmount());
            }

            if (total.compareTo(request.getAmount()) != 0) {
                throw new ApiException("Split total does not match amount", HttpStatus.FORBIDDEN);
            }

            for (DirectSplitRequest.ExactSplit split : request.getExactSplits()) {

                if (!split.getUserId().equals(payer.getId())) {

                    User user = userRepository.findById(split.getUserId())
                            .orElseThrow();

                    ledgerService.createEntry(
                            group,
                            user,
                            payer,
                            split.getAmount(),
                            ReferenceType.EXPENSE,
                            expense.getId()
                    );
                }
            }
        }
    }

    @Transactional
    @CacheEvict(value = {"groupDetail", "dashboardSummary", "weeklyChart"}, allEntries = true)
    public void cancelExpense(Long expenseId) {

        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ApiException("Expense not found", HttpStatus.FORBIDDEN));
        authorizationService.validateGroupMember(expense.getGroup().getId());
        if (expense.isCancelled()) {
            throw new ApiException("Expense already cancelled", HttpStatus.FORBIDDEN);
        }

        List<LedgerEntry> entries =
                ledgerRepository.findByReferenceTypeAndReferenceId(
                        ReferenceType.EXPENSE,
                        expense.getId()
                );

        for (LedgerEntry entry : entries) {

            ledgerService.createEntry(
                    entry.getGroup(),
                    entry.getToUser(),      // reverse
                    entry.getFromUser(),
                    entry.getAmount(),
                    ReferenceType.EXPENSE,
                    expense.getId()
            );
        }
        List<ExpenseSplit> splits = splitRepository.findByExpense_Id(expenseId);
        splitRepository.deleteAll(splits);
        expense.setCancelled(true);
        expenseRepository.save(expense);
    }

    @Transactional
    @CacheEvict(value = {"groupDetail", "dashboardSummary", "weeklyChart"}, allEntries = true)
    public void editExpense(Long expenseId,
                            EditExpenseRequest request) {

        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ApiException("Expense not found", HttpStatus.FORBIDDEN));
        authorizationService.validateGroupMember(expense.getGroup().getId());
        if (expense.isCancelled()) {
            throw new ApiException("Cannot edit cancelled expense", HttpStatus.FORBIDDEN);
        }

        List<LedgerEntry> oldEntries =
                ledgerRepository.findByReferenceTypeAndReferenceId(
                        ReferenceType.EXPENSE,
                        expense.getId()
                );

        for (LedgerEntry entry : oldEntries) {
            ledgerService.createEntry(
                    entry.getGroup(),
                    entry.getToUser(),
                    entry.getFromUser(),
                    entry.getAmount(),
                    ReferenceType.EXPENSE,
                    expense.getId()
            );
        }
        List<ExpenseSplit> oldSplits = splitRepository.findByExpense_Id(expenseId);
        splitRepository.deleteAll(oldSplits);
        User newPayer = userRepository.findById(request.getPayerId())
                .orElseThrow(() -> new ApiException("Payer not found", HttpStatus.FORBIDDEN));

        expense.setDescription(request.getDescription());
        expense.setTotalAmount(request.getAmount());
        expense.setPaidBy(newPayer);

        expenseRepository.save(expense);

        if (request.getSplitType() == SplitType.EQUAL) {

            int size = request.getUserIds().size();
            BigDecimal splitAmount =
                    request.getAmount().divide(BigDecimal.valueOf(size));

            for (Long userId : request.getUserIds()) {

                if (!userId.equals(newPayer.getId())) {

                    User user = userRepository.findById(userId)
                            .orElseThrow();

                    ledgerService.createEntry(
                            expense.getGroup(),
                            user,
                            newPayer,
                            splitAmount,
                            ReferenceType.EXPENSE,
                            expense.getId()
                    );
                }
            }
        }

        if (request.getSplitType() == SplitType.EXACT) {

            BigDecimal total = BigDecimal.ZERO;

            for (DirectSplitRequest.ExactSplit split : request.getExactSplits()) {
                total = total.add(split.getAmount());
            }

            if (total.compareTo(request.getAmount()) != 0) {
                throw new ApiException("Split total mismatch", HttpStatus.FORBIDDEN);
            }

            for (DirectSplitRequest.ExactSplit split : request.getExactSplits()) {

                if (!split.getUserId().equals(newPayer.getId())) {

                    User user = userRepository.findById(split.getUserId())
                            .orElseThrow();

                    ledgerService.createEntry(
                            expense.getGroup(),
                            user,
                            newPayer,
                            split.getAmount(),
                            ReferenceType.EXPENSE,
                            expense.getId()
                    );
                }
            }
        }
    }

    private String generateGroupCode() {
        return UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase();
    }
    @Transactional
    public List<ExpenseListResponse> getGroupExpenses(Long groupId) {

        authorizationService.validateGroupMember(groupId);

        return expenseRepository.findByGroup_IdAndIsCancelledFalse(groupId)
                .stream()
                .map(expense -> ExpenseListResponse.builder()
                        .id(expense.getId())
                        .description(expense.getDescription())
                        .totalAmount(expense.getTotalAmount())
                        .groupName(expense.getGroup().getName())
                        .isCancelled(expense.isCancelled())
                        .paidByUserId(expense.getPaidBy().getId())
                        .paidByName(expense.getPaidBy().getFullName())
                        .createdAt(expense.getCreatedAt())
                        .category(expense.getCategory())
                        .build())
                .toList();
    }
    @Transactional
    public List<ExpenseListResponse> getMyExpensesInGroup(Long groupId) {

        User currentUser = authorizationService.getCurrentUser();

        authorizationService.validateGroupMember(groupId);

        return expenseRepository
                .findByGroup_IdAndPaidBy_Id(groupId, currentUser.getId())
                .stream()
                .map(expense -> ExpenseListResponse.builder()
                        .id(expense.getId())
                        .description(expense.getDescription())
                        .totalAmount(expense.getTotalAmount())
                        .groupName(expense.getGroup().getName())
                        .paidByUserId(expense.getPaidBy().getId())
                        .paidByName(expense.getPaidBy().getFullName())
                        .createdAt(expense.getCreatedAt())
                        .category(expense.getCategory())
                        .build())
                .toList();
    }
    @Transactional
    public List<ExpenseListResponse> getMyExpenses() {

        User currentUser = authorizationService.getCurrentUser();

        return expenseRepository
                .findByPaidBy_IdAndIsCancelledFalse(currentUser.getId())
                .stream()
                .map(expense -> ExpenseListResponse.builder()
                        .id(expense.getId())
                        .description(expense.getDescription())
                        .totalAmount(expense.getTotalAmount())
                        .groupName(expense.getGroup().getName())
                        .paidByUserId(expense.getPaidBy().getId())
                        .paidByName(expense.getPaidBy().getFullName())
                        .createdAt(expense.getCreatedAt())
                        .category(expense.getCategory())
                        .build())
                .toList();
    }
    @Transactional
    public List<ExpenseListResponse> getGroupExpenses(Long groupId, Long userId) {
        // Skip auth validation — already validated on main thread in getGroupDetail
        return expenseRepository.findByGroup_IdAndIsCancelledFalse(groupId)
                .stream()
                .map(expense -> ExpenseListResponse.builder()
                        .id(expense.getId())
                        .description(expense.getDescription())
                        .totalAmount(expense.getTotalAmount())
                        .groupName(expense.getGroup().getName())
                        .isCancelled(expense.isCancelled())
                        .paidByUserId(expense.getPaidBy().getId())
                        .paidByName(expense.getPaidBy().getFullName())
                        .createdAt(expense.getCreatedAt())
                        .category(expense.getCategory())
                        .build())
                .toList();
    }

    public List<ExpenseListResponse> getFilteredGroupExpenses(
            Long groupId,
            String category,
            LocalDateTime from,
            LocalDateTime to) {

        authorizationService.validateGroupMember(groupId);

        Pageable unpaged = Pageable.unpaged();

        return expenseRepository.findWithFilters(groupId, category, from, to, unpaged)
                .getContent()
                .stream()
                .map(expense -> ExpenseListResponse.builder()
                        .id(expense.getId())
                        .description(expense.getDescription())
                        .category(expense.getCategory())
                        .totalAmount(expense.getTotalAmount())
                        .groupName(expense.getGroup().getName())
                        .isCancelled(expense.isCancelled())
                        .paidByUserId(expense.getPaidBy().getId())
                        .paidByName(expense.getPaidBy().getFullName())
                        .createdAt(expense.getCreatedAt())
                        .build())
                .toList();
    }

    public PagedResponse<ExpenseListResponse> getGroupExpensesPaged(
            Long groupId,
            String category,
            LocalDateTime from,
            LocalDateTime to,
            int page,
            int size) {

        authorizationService.validateGroupMember(groupId);

        Pageable pageable = PageRequest.of(
                page, size, Sort.by("createdAt").descending());

        Page<Expense> expensePage = expenseRepository
                .findWithFilters(groupId, category, from, to, pageable);

        List<ExpenseListResponse> content = expensePage.getContent()
                .stream()
                .map(expense -> ExpenseListResponse.builder()
                        .id(expense.getId())
                        .description(expense.getDescription())
                        .category(expense.getCategory())
                        .totalAmount(expense.getTotalAmount())
                        .groupName(expense.getGroup().getName())
                        .isCancelled(expense.isCancelled())
                        .paidByUserId(expense.getPaidBy().getId())
                        .paidByName(expense.getPaidBy().getFullName())
                        .createdAt(expense.getCreatedAt())
                        .build())
                .toList();

        return PagedResponse.<ExpenseListResponse>builder()
                .content(content)
                .page(expensePage.getNumber())
                .size(expensePage.getSize())
                .totalElements(expensePage.getTotalElements())
                .totalPages(expensePage.getTotalPages())
                .last(expensePage.isLast())
                .build();
    }

    public List<PersonalExpense> getFilteredPersonalExpenses(
            String category,
            LocalDateTime from,
            LocalDateTime to) {

        User currentUser = authorizationService.getCurrentUser();

        return personalExpenseRepository
                .findWithFilters(currentUser.getId(), category, from, to, Pageable.unpaged())
                .getContent();
    }

    public List<ExpenseListResponse> searchGroupExpenses(Long groupId, String query) {

        authorizationService.validateGroupMember(groupId);

        return expenseRepository.searchExpenses(groupId, query)
                .stream()
                .map(expense -> ExpenseListResponse.builder()
                        .id(expense.getId())
                        .description(expense.getDescription())
                        .category(expense.getCategory())
                        .totalAmount(expense.getTotalAmount())
                        .groupName(expense.getGroup().getName())
                        .isCancelled(expense.isCancelled())
                        .paidByUserId(expense.getPaidBy().getId())
                        .paidByName(expense.getPaidBy().getFullName())
                        .createdAt(expense.getCreatedAt())
                        .build())
                .toList();
    }

    public List<PersonalExpense> searchPersonalExpenses(String query) {

        User currentUser = authorizationService.getCurrentUser();

        return personalExpenseRepository
                .searchPersonalExpenses(currentUser.getId(), query);
    }
    public PagedResponse<PersonalExpense> getPersonalExpensesPaged(
            String category,
            LocalDateTime from,
            LocalDateTime to,
            int page,
            int size) {

        User currentUser = authorizationService.getCurrentUser();

        Pageable pageable = PageRequest.of(
                page, size, Sort.by("expenseDate").descending());

        Page<PersonalExpense> expensePage = personalExpenseRepository
                .findWithFilters(currentUser.getId(), category, from, to, pageable);

        return PagedResponse.<PersonalExpense>builder()
                .content(expensePage.getContent())
                .page(expensePage.getNumber())
                .size(expensePage.getSize())
                .totalElements(expensePage.getTotalElements())
                .totalPages(expensePage.getTotalPages())
                .last(expensePage.isLast())
                .build();
    }
}
