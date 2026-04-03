package com.shrihari.smartpaybackend.settlement;

import com.shrihari.smartpaybackend.exception.ApiException;
import com.shrihari.smartpaybackend.group.Group;
import com.shrihari.smartpaybackend.group.GroupMemberRepository;
import com.shrihari.smartpaybackend.group.GroupRepository;
import com.shrihari.smartpaybackend.ledger.*;
import com.shrihari.smartpaybackend.risk.RiskService;
import com.shrihari.smartpaybackend.user.AuthorizationService;
import com.shrihari.smartpaybackend.user.User;
import com.shrihari.smartpaybackend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SettlementService {

    private final SettlementRepository settlementRepository;
    private final LedgerService ledgerService;
    private final LedgerRepository ledgerRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final AuthorizationService authorizationService;
    private final RiskService riskService;

    @Transactional
    public SettlementInitiateResponse initiateSettlement(
            Long groupId,
            Long fromUserId,
            Long toUserId,
            BigDecimal amount) {

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException("Settlement amount must be positive", HttpStatus.FORBIDDEN);
        }

        authorizationService.validateGroupMember(groupId);

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ApiException("Group not found", HttpStatus.FORBIDDEN));

        User fromUser = userRepository.findById(fromUserId)
                .orElseThrow(() -> new ApiException("From user not found", HttpStatus.FORBIDDEN));

        User toUser = userRepository.findById(toUserId)
                .orElseThrow(() -> new ApiException("To user not found", HttpStatus.FORBIDDEN));

        // Risk check
        riskService.evaluateAndBlock(fromUser, toUser, amount);
        // Build UPI deep link if receiver has UPI ID
        String upiLink = null;
        if (toUser.getUpiId() != null && !toUser.getUpiId().isBlank()) {
            upiLink = String.format(
                    "upi://pay?pa=%s&pn=%s&am=%s&cu=INR&tn=Settlement",
                    toUser.getUpiId(),
                    toUser.getFullName().replace(" ", "%20"),
                    amount.toPlainString()
            );
        }

        Settlement settlement = Settlement.builder()
                .group(group)
                .fromUser(fromUser)
                .toUser(toUser)
                .amount(amount)
                .status(SettlementStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        settlementRepository.save(settlement);

        return SettlementInitiateResponse.builder()
                .settlementId(settlement.getId())
                .amount(amount)
                .upiLink(upiLink)
                .upiAvailable(upiLink != null)
                .build();
    }

    // Payer taps "I've Paid"
    @Transactional
    public void claimPaid(Long settlementId) {

        User currentUser = authorizationService.getCurrentUser();

        Settlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new ApiException("Settlement not found", HttpStatus.FORBIDDEN));

        if (!settlement.getFromUser().getId().equals(currentUser.getId())) {
            throw new ApiException("Only the payer can claim this settlement", HttpStatus.FORBIDDEN);
        }

        if (settlement.getStatus() != SettlementStatus.PENDING) {
            throw new ApiException("Settlement already processed", HttpStatus.FORBIDDEN);
        }

        settlement.setStatus(SettlementStatus.PENDING_CONFIRMATION);
        settlementRepository.save(settlement);
    }

    // Receiver taps "Confirm Received"
    @Transactional
    @CacheEvict(value = {"groupDetail", "dashboardSummary", "weeklyChart"}, allEntries = true)
    public void confirmSettlement(Long settlementId) {

        User currentUser = authorizationService.getCurrentUser();

        Settlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new ApiException("Settlement not found", HttpStatus.FORBIDDEN));

        if (!settlement.getToUser().getId().equals(currentUser.getId())) {
            throw new ApiException("Only the receiver can confirm this settlement", HttpStatus.FORBIDDEN);
        }

        if (settlement.getStatus() != SettlementStatus.PENDING_CONFIRMATION) {
            throw new ApiException("Settlement is not pending confirmation", HttpStatus.FORBIDDEN);
        }

        ledgerService.createEntry(
                settlement.getGroup(),
                settlement.getToUser(),
                settlement.getFromUser(),
                settlement.getAmount(),
                ReferenceType.SETTLEMENT,
                settlement.getId()
        );

        settlement.setStatus(SettlementStatus.COMPLETED);
        settlementRepository.save(settlement);
    }

    // Receiver taps "Didn't Receive"
    @Transactional
    public void disputeSettlement(Long settlementId) {

        User currentUser = authorizationService.getCurrentUser();

        Settlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new ApiException("Settlement not found", HttpStatus.FORBIDDEN));

        if (!settlement.getToUser().getId().equals(currentUser.getId())) {
            throw new ApiException("Only the receiver can dispute this settlement", HttpStatus.FORBIDDEN);
        }

        if (settlement.getStatus() != SettlementStatus.PENDING_CONFIRMATION) {
            throw new ApiException("Settlement is not pending confirmation", HttpStatus.FORBIDDEN);
        }

        settlement.setStatus(SettlementStatus.DISPUTED);
        settlementRepository.save(settlement);
    }

    // Receiver sees pending settlements to confirm
    public List<Settlement> getPendingConfirmations() {

        User currentUser = authorizationService.getCurrentUser();

        return settlementRepository.findByToUser_IdAndStatus(
                currentUser.getId(),
                SettlementStatus.PENDING_CONFIRMATION
        );
    }
}