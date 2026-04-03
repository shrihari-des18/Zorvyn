package com.shrihari.smartpaybackend.security;

import com.shrihari.smartpaybackend.exception.ApiException;
import com.shrihari.smartpaybackend.user.Role;
import com.shrihari.smartpaybackend.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class RoleGuard {

    public void requireAdmin(User user) {
        if (user.getRole() != Role.ADMIN) {
            throw new ApiException("Access denied. Admin role required.", HttpStatus.FORBIDDEN);
        }
    }

    public void requireAnalystOrAbove(User user) {
        if (user.getRole() == Role.VIEWER) {
            throw new ApiException("Access denied. Analyst role or above required.", HttpStatus.FORBIDDEN);
        }
    }

    public void requireActive(User user) {
        if (!user.isActive()) {
            throw new ApiException("Your account has been deactivated.", HttpStatus.FORBIDDEN);
        }
    }
}