package com.shrihari.smartpaybackend.transfer;

import com.shrihari.smartpaybackend.common.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/transfers")
@RequiredArgsConstructor
@Tag(name = "Transfers", description = "Peer-to-peer money transfers")
public class TransferController {

    private final TransferService transferService;

    @PostMapping("/initiate")
    public ApiResponse<?> initiate(@RequestBody InitiateRequest request) {

        return new ApiResponse<>(
                true,
                "Transfer initiated",
                transferService.initiateTransfer(
                        request.getToUserId(),
                        request.getAmount(),
                        request.getNote()
                )
        );
    }

    @PostMapping("/{transferId}/claim")
    public ApiResponse<?> claim(@PathVariable Long transferId) {

        transferService.claimSent(transferId);

        return new ApiResponse<>(true, "Transfer claimed as sent", null);
    }

    @PostMapping("/{transferId}/confirm")
    public ApiResponse<?> confirm(@PathVariable Long transferId) {

        transferService.confirmReceived(transferId);

        return new ApiResponse<>(true, "Transfer confirmed", null);
    }

    @PostMapping("/{transferId}/dispute")
    public ApiResponse<?> dispute(@PathVariable Long transferId) {

        transferService.disputeTransfer(transferId);

        return new ApiResponse<>(true, "Transfer disputed", null);
    }

    @GetMapping("/pending-confirmations")
    public ApiResponse<?> pendingConfirmations() {

        return new ApiResponse<>(
                true,
                "Pending confirmations",
                transferService.getPendingConfirmations()
        );
    }

    @GetMapping("/my")
    public ApiResponse<?> myTransfers() {

        return new ApiResponse<>(
                true,
                "My transfers",
                transferService.getMyTransfers()
        );
    }

    @Data
    static class InitiateRequest {
        private Long toUserId;
        private BigDecimal amount;
        private String note;
    }
}