package com.shrihari.smartpaybackend.group.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupMemberResponse {

    private Long userId;
    private String fullName;
    private String identifier;
    private String role;
    private Boolean isFriend;
}
