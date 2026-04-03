package com.shrihari.smartpaybackend.user;

import com.shrihari.smartpaybackend.exception.ApiException;
import com.shrihari.smartpaybackend.group.GroupMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthorizationService {

    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;

    public User getCurrentUser() {

        String identifier = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        return userRepository
                .findByEmailOrPhoneNumber(identifier, identifier)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.FORBIDDEN));
    }

    public void validateGroupMember(Long groupId) {

        User user = getCurrentUser();

        boolean isMember =
                groupMemberRepository
                        .existsByGroup_IdAndUser_Id(groupId, user.getId());

        if (!isMember) {
            throw new ApiException("Access denied: Not a group member", HttpStatus.FORBIDDEN);
        }
    }
}
