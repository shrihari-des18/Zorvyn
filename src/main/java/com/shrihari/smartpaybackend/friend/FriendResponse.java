package com.shrihari.smartpaybackend.friend;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FriendResponse {
    private Long id;
    private String fullName;
    private String email;
    private String phoneNumber;
}