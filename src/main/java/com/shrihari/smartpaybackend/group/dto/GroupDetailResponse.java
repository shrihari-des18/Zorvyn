package com.shrihari.smartpaybackend.group.dto;

import com.shrihari.smartpaybackend.expense.dto.ExpenseListResponse;
import com.shrihari.smartpaybackend.ledger.dto.GroupBalanceResponse;
import com.shrihari.smartpaybackend.ledger.dto.TransactionResponse;
import com.shrihari.smartpaybackend.user.UserProfileResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupDetailResponse {
    private GroupResponse group;
    private UserProfileResponse currentUser;
    private List<GroupMemberResponse> members;
    private List<TransactionResponse> transactions;
    private List<GroupBalanceResponse> balances;
    private BigDecimal myBalance;
    private List<ExpenseListResponse> expenses;
}