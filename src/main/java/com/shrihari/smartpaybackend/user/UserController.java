package com.shrihari.smartpaybackend.user;

import com.shrihari.smartpaybackend.common.ApiResponse;
import com.shrihari.smartpaybackend.exception.ApiException;
import com.shrihari.smartpaybackend.security.RoleGuard;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User profile and management")
public class UserController {

    private final AuthService userService;
    private final UserRepository userRepository;
    private final AuthorizationService authorizationService;
    private final RoleGuard roleGuard;

    @GetMapping("/me")
    public ApiResponse<?> getProfile() {
        return new ApiResponse<>(
                true,
                "User profile fetched",
                userService.getCurrentUser()
        );
    }

    @PutMapping("/upi")
    public ApiResponse<?> updateUpiId(@RequestBody UpiRequest request) {

        if (request.getUpiId() == null || request.getUpiId().isBlank()) {
            throw new ApiException("UPI ID cannot be empty", HttpStatus.FORBIDDEN);
        }

        User currentUser = authorizationService.getCurrentUser();
        currentUser.setUpiId(request.getUpiId());
        userRepository.save(currentUser);

        return new ApiResponse<>(true, "UPI ID updated successfully", null);
    }

    @PutMapping("/{userId}/status")
    public ApiResponse<?> updateUserStatus(
            @PathVariable Long userId,
            @RequestBody UserStatusRequest request) {

        User currentUser = authorizationService.getCurrentUser();
        roleGuard.requireAdmin(currentUser);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("User not found"));

        user.setActive(request.isActive());
        userRepository.save(user);

        return new ApiResponse<>(true,
                "User status updated to " + (request.isActive() ? "active" : "inactive"),
                null);
    }

    @PutMapping("/{userId}/role")
    public ApiResponse<?> updateUserRole(
            @PathVariable Long userId,
            @RequestBody UserRoleRequest request) {

        User currentUser = authorizationService.getCurrentUser();
        roleGuard.requireAdmin(currentUser);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("User not found"));

        user.setRole(request.getRole());
        userRepository.save(user);

        return new ApiResponse<>(true, "User role updated to " + request.getRole(), null);
    }

    @GetMapping("/all")
    public ApiResponse<?> getAllUsers() {

        User currentUser = authorizationService.getCurrentUser();
        roleGuard.requireAdmin(currentUser);

        List<User> users = userRepository.findAll();
        return new ApiResponse<>(true, "All users fetched", users);
    }

    @Data
    static class UserStatusRequest {
        private boolean isActive;
    }

    @Data
    static class UserRoleRequest {
        private Role role;
    }

    @Data
    static class UpiRequest {
        private String upiId;
    }
}