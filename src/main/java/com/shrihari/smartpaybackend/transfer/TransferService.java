package com.shrihari.smartpaybackend.transfer;

import com.shrihari.smartpaybackend.exception.ApiException;
import com.shrihari.smartpaybackend.ledger.LedgerEntry;
import com.shrihari.smartpaybackend.ledger.LedgerRepository;
import com.shrihari.smartpaybackend.ledger.LedgerService;
import com.shrihari.smartpaybackend.ledger.ReferenceType;
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
public class TransferService {

    private final TransferRepository transferRepository;
    private final UserRepository userRepository;
    private final LedgerService ledgerService;
    private final LedgerRepository ledgerRepository;
    private final AuthorizationService authorizationService;
    private final RiskService riskService;

    @Transactional
    public TransferResponse initiateTransfer(Long toUserId, BigDecimal amount, String note) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException("Amount must be positive", HttpStatus.FORBIDDEN);
        }

        User sender = authorizationService.getCurrentUser();

        if (sender.getId().equals(toUserId)) {
            throw new ApiException("Cannot send money to yourself", HttpStatus.FORBIDDEN);
        }

        User receiver = userRepository.findById(toUserId)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.FORBIDDEN));

        if (receiver.getUpiId() == null || receiver.getUpiId().isBlank()) {
            throw new ApiException(
                    receiver.getFullName() + " hasn't added their UPI ID yet",
                    HttpStatus.FORBIDDEN);
        }

        // Risk check — throws RiskBlockedException if blocked
        riskService.evaluateAndBlock(sender, receiver, amount);

        // Build UPI deep link
        String upiLink = String.format(
                "upi://pay?pa=%s&pn=%s&am=%s&cu=INR&tn=%s",
                receiver.getUpiId(),
                receiver.getFullName().replace(" ", "%20"),
                amount.toPlainString(),
                note != null ? note.replace(" ", "%20") : "Payment"
        );

        Transfer transfer = Transfer.builder()
                .fromUser(sender)
                .toUser(receiver)
                .amount(amount)
                .note(note)
                .upiLink(upiLink)
                .status(TransferStatus.INITIATED)
                .createdAt(LocalDateTime.now())
                .build();

        transferRepository.save(transfer);

        return TransferResponse.builder()
                .transferId(transfer.getId())
                .upiLink(upiLink)
                .amount(amount)
                .toUserName(receiver.getFullName())
                .toUpiId(receiver.getUpiId())
                .status(transfer.getStatus().name())
                .build();
    }

    @Transactional
    public void claimSent(Long transferId) {

        User sender = authorizationService.getCurrentUser();

        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new ApiException("Transfer not found", HttpStatus.FORBIDDEN));

        if (!transfer.getFromUser().getId().equals(sender.getId())) {
            throw new ApiException("Only sender can claim this transfer", HttpStatus.FORBIDDEN);
        }

        if (transfer.getStatus() != TransferStatus.INITIATED) {
            throw new ApiException("Transfer already processed", HttpStatus.FORBIDDEN);
        }

        transfer.setStatus(TransferStatus.PENDING_CONFIRMATION);
        transferRepository.save(transfer);
    }

    @Transactional
    @CacheEvict(value = {"dashboardSummary", "weeklyChart"}, allEntries = true)
    public void confirmReceived(Long transferId) {

        User receiver = authorizationService.getCurrentUser();

        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new ApiException("Transfer not found", HttpStatus.FORBIDDEN));

        if (!transfer.getToUser().getId().equals(receiver.getId())) {
            throw new ApiException("Only receiver can confirm this transfer", HttpStatus.FORBIDDEN);
        }

        if (transfer.getStatus() != TransferStatus.PENDING_CONFIRMATION) {
            throw new ApiException("Transfer is not pending confirmation", HttpStatus.FORBIDDEN);
        }

        // Create ledger entry — sender paid receiver
        ledgerService.createEntry(
                null,
                transfer.getFromUser(),
                transfer.getToUser(),
                transfer.getAmount(),
                ReferenceType.TRANSFER,
                transfer.getId()
        );

        // Auto offset existing debt between the two users
        autoOffsetDebt(transfer);

        transfer.setStatus(TransferStatus.COMPLETED);
        transfer.setConfirmedAt(LocalDateTime.now());
        transferRepository.save(transfer);
    }

    @Transactional
    public void disputeTransfer(Long transferId) {

        User receiver = authorizationService.getCurrentUser();

        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new ApiException("Transfer not found", HttpStatus.FORBIDDEN));

        if (!transfer.getToUser().getId().equals(receiver.getId())) {
            throw new ApiException("Only receiver can dispute this transfer", HttpStatus.FORBIDDEN);
        }

        if (transfer.getStatus() != TransferStatus.PENDING_CONFIRMATION) {
            throw new ApiException("Transfer is not pending confirmation", HttpStatus.FORBIDDEN);
        }

        transfer.setStatus(TransferStatus.DISPUTED);
        transferRepository.save(transfer);
    }

    public List<Transfer> getPendingConfirmations() {

        User currentUser = authorizationService.getCurrentUser();

        return transferRepository.findByToUser_IdAndStatus(
                currentUser.getId(),
                TransferStatus.PENDING_CONFIRMATION
        );
    }

    public List<Transfer> getMyTransfers() {

        User currentUser = authorizationService.getCurrentUser();

        return transferRepository.findByFromUser_IdOrToUser_IdOrderByCreatedAtDesc(
                currentUser.getId(),
                currentUser.getId()
        );
    }

    private void autoOffsetDebt(Transfer transfer) {

        Long senderId = transfer.getFromUser().getId();
        Long receiverId = transfer.getToUser().getId();
        BigDecimal transferAmount = transfer.getAmount();

        // Find all ledger entries where sender owes receiver
        List<LedgerEntry> debts = ledgerRepository
                .findByFromUser_IdOrToUser_Id(senderId, senderId)
                .stream()
                .filter(e -> e.getReferenceType() == ReferenceType.EXPENSE
                        && e.getFromUser().getId().equals(senderId)
                        && e.getToUser().getId().equals(receiverId))
                .toList();

        BigDecimal totalDebt = debts.stream()
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalDebt.compareTo(BigDecimal.ZERO) <= 0) return;

        // Offset up to the transfer amount
        BigDecimal offsetAmount = totalDebt.min(transferAmount);

        ledgerService.createEntry(
                null,
                transfer.getToUser(),   // reverse — receiver cancels sender's debt
                transfer.getFromUser(),
                offsetAmount,
                ReferenceType.TRANSFER,
                transfer.getId()
        );
    }
}