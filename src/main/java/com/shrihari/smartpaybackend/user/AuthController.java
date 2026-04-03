package com.shrihari.smartpaybackend.user;

import com.shrihari.smartpaybackend.common.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register and login")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ApiResponse<?> register(@RequestBody RegisterRequest request) {

        String token = authService.register(
                request.getEmail(),
                request.getPhoneNumber(),
                request.getPassword(),
                request.getFullName(),
                request.getRole()
        );

        return new ApiResponse<>(true, "User registered successfully", token);
    }

    @PostMapping("/login")
    public ApiResponse<?> login(@RequestBody LoginRequest request) {

        String token = authService.login(
                request.getIdentifier(),
                request.getPassword()
        );

        return new ApiResponse<>(true, "Login successful", token);
    }

    @Data
    static class RegisterRequest {
        private String email;
        private String phoneNumber;
        private String password;
        private String fullName;
        private Role role;
    }

    @Data
    static class LoginRequest {
        private String identifier;
        private String password;
    }
}


