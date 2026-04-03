package com.shrihari.smartpaybackend.friend;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PendingRequestResponse {
    private Long requestId;
    private Long requesterId;
    private String requesterName;
    private String requesterPhone;
    private LocalDateTime createdAt;
}