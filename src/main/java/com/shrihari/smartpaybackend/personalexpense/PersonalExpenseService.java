package com.shrihari.smartpaybackend.personalexpense;

import com.shrihari.smartpaybackend.exception.ApiException;
import com.shrihari.smartpaybackend.ledger.LedgerService;
import com.shrihari.smartpaybackend.ledger.ReferenceType;
import com.shrihari.smartpaybackend.user.AuthorizationService;
import com.shrihari.smartpaybackend.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PersonalExpenseService {

    private final PersonalExpenseRepository personalExpenseRepository;
    private final LedgerService ledgerService;
    private final AuthorizationService authorizationService;

    @Transactional
    public PersonalExpense createPersonalExpense(
            String description,
            BigDecimal amount,
            String category,
            String note,
            LocalDateTime expenseDate) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException("Amount must be positive", HttpStatus.FORBIDDEN);
        }

        User currentUser = authorizationService.getCurrentUser();

        PersonalExpense expense = PersonalExpense.builder()
                .user(currentUser)
                .description(description)
                .amount(amount)
                .category(category)
                .note(note)
                .expenseDate(expenseDate != null ? expenseDate : LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

        personalExpenseRepository.save(expense);

        ledgerService.createEntry(
                null,                        // no group
                currentUser,                 // fromUser (spent by)
                currentUser,                 // toUser (same user)
                amount,
                ReferenceType.PERSONAL_EXPENSE,
                expense.getId()
        );

        return expense;
    }

    public List<PersonalExpense> getMyPersonalExpenses() {
        User currentUser = authorizationService.getCurrentUser();
        return personalExpenseRepository
                .findByUser_IdOrderByExpenseDateDesc(currentUser.getId());
    }

}