package com.labmentix.docsign.auth.dto;

import com.labmentix.docsign.auth.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public class AuthDto {

    // ── Register ───────────────────────────────────────────────

    public record RegisterRequest(
            @NotBlank(message = "Name is required")
            @Size(min = 2, max = 100)
            String name,

            @NotBlank(message = "Email is required")
            @Email(message = "Must be a valid email address")
            String email,

            @NotBlank(message = "Password is required")
            @Size(min = 8, message = "Password must be at least 8 characters")
            String password
    ) {}

    // ── Login ──────────────────────────────────────────────────

    public record LoginRequest(
            @NotBlank(message = "Email is required")
            @Email
            String email,

            @NotBlank(message = "Password is required")
            String password
    ) {}

    // ── Refresh ────────────────────────────────────────────────

    public record RefreshRequest(
            @NotBlank(message = "Refresh token is required")
            String refreshToken
    ) {}

    // ── Responses ─────────────────────────────────────────────

    public record AuthResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,             // seconds
            UserInfo user
    ) {
        public static AuthResponse of(String access, String refresh, long expiresMs, User user) {
            return new AuthResponse(
                    access,
                    refresh,
                    "Bearer",
                    expiresMs / 1000,
                    UserInfo.from(user)
            );
        }
    }

    public record UserInfo(
            UUID id,
            String name,
            String email,
            String role,
            Instant createdAt
    ) {
        public static UserInfo from(User user) {
            return new UserInfo(
                    user.getId(),
                    user.getName(),
                    user.getEmail(),
                    user.getRole().name(),
                    user.getCreatedAt()
            );
        }
    }

    public record MessageResponse(String message) {}
}