package com.labmentix.docsign.auth.service;

import com.labmentix.docsign.auth.dto.AuthDto.*;
import com.labmentix.docsign.auth.entity.RefreshToken;
import com.labmentix.docsign.auth.entity.User;
import com.labmentix.docsign.auth.repository.RefreshTokenRepository;
import com.labmentix.docsign.auth.repository.UserRepository;
import com.labmentix.docsign.auth.security.JwtService;
import com.labmentix.docsign.common.exception.BadRequestException;
import com.labmentix.docsign.common.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Value("${app.jwt.access-token-expiry-ms}")
    private long accessTokenExpiryMs;

    @Value("${app.jwt.refresh-token-expiry-ms}")
    private long refreshTokenExpiryMs;

    // ── Register ───────────────────────────────────────────────

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BadRequestException("An account with this email already exists");
        }

        User user = User.builder()
                .name(request.name().trim())
                .email(request.email().toLowerCase().trim())
                .password(passwordEncoder.encode(request.password()))
                .role(User.Role.OWNER)       // default role on self-registration
                .active(true)
                .build();

        userRepository.save(user);
        log.info("New user registered: {} ({})", user.getEmail(), user.getId());

        return issueTokenPair(user);
    }

    // ── Login ──────────────────────────────────────────────────

    @Transactional
    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.email().toLowerCase().trim(),
                            request.password()
                    )
            );
        } catch (BadCredentialsException e) {
            throw new UnauthorizedException("Invalid email or password");
        }

        User user = userRepository.findByEmail(request.email().toLowerCase().trim())
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        // Revoke existing refresh tokens (one active session policy)
        refreshTokenRepository.revokeAllUserTokens(user);

        log.info("User logged in: {}", user.getEmail());
        return issueTokenPair(user);
    }

    // ── Refresh ────────────────────────────────────────────────

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        RefreshToken storedToken = refreshTokenRepository
                .findByToken(request.refreshToken())
                .orElseThrow(() -> new UnauthorizedException("Refresh token not found"));

        if (!storedToken.isValid()) {
            // Token is expired or revoked — revoke all for safety (detect token theft)
            refreshTokenRepository.revokeAllUserTokens(storedToken.getUser());
            throw new UnauthorizedException("Refresh token is expired or revoked. Please log in again.");
        }

        // Rotate: revoke old, issue new pair
        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        User user = storedToken.getUser();
        log.debug("Refresh token rotated for user: {}", user.getEmail());
        return issueTokenPair(user);
    }

    // ── Logout ─────────────────────────────────────────────────

    @Transactional
    public void logout(User currentUser) {
        int revoked = refreshTokenRepository.revokeAllUserTokens(currentUser);
        log.info("User logged out: {} — {} refresh tokens revoked", currentUser.getEmail(), revoked);
    }

    // ── Internal helpers ──────────────────────────────────────

    private AuthResponse issueTokenPair(User user) {
        String accessToken  = jwtService.generateAccessToken(user);
        String refreshToken = generateAndSaveRefreshToken(user);
        return AuthResponse.of(accessToken, refreshToken, accessTokenExpiryMs, user);
    }

    private String generateAndSaveRefreshToken(User user) {
        String tokenValue = UUID.randomUUID().toString() + "-" + UUID.randomUUID();

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(tokenValue)
                .expiresAt(Instant.now().plusMillis(refreshTokenExpiryMs))
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);
        return tokenValue;
    }
}