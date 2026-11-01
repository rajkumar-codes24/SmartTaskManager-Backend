package com.smarttaskmanager.auth.integration;

import com.smarttaskmanager.auth.entity.PasswordResetToken;
import com.smarttaskmanager.auth.entity.RefreshToken;
import com.smarttaskmanager.auth.repository.PasswordResetTokenRepository;
import com.smarttaskmanager.auth.repository.RefreshTokenRepository;
import com.smarttaskmanager.auth.service.TokenService;
import com.smarttaskmanager.common.config.ApplicationProperties;
import com.smarttaskmanager.common.ratelimit.InMemoryRateLimiter;
import com.smarttaskmanager.user.entity.Role;
import com.smarttaskmanager.user.entity.User;
import com.smarttaskmanager.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordResetIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ApplicationProperties applicationProperties;

    @Autowired
    private InMemoryRateLimiter rateLimiter;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TokenService tokenService = new TokenService();

    private User user;

    @BeforeEach
    void setUp() {
        rateLimiter.clearAll();

        user = User.builder()
                .fullName("Alice Smith")
                .email("reset-test@example.com")
                .passwordHash(passwordEncoder.encode("OldPassword@123"))
                .role(Role.ROLE_USER)
                .enabled(true)
                .emailVerified(true)
                .build();
        user = userRepository.save(user);
    }

    @AfterEach
    void tearDown() {
        passwordResetTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ==================== Forgot Password Tests ====================

    @Test
    @DisplayName("Forgot password returns 200 for existing email")
    void forgotPassword_existingEmail_returns200() throws Exception {
        String requestJson = """
                {
                    "email": "reset-test@example.com"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType("application/json")
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Verify a reset token was created
        assertThat(passwordResetTokenRepository.findAll()).hasSize(1);
        PasswordResetToken savedToken = passwordResetTokenRepository.findAll().get(0);
        assertThat(savedToken.getUser().getId()).isEqualTo(user.getId());
        assertThat(savedToken.isConsumed()).isFalse();
        assertThat(savedToken.isExpired()).isFalse();
    }

    @Test
    @DisplayName("Forgot password returns 200 for non-existent email (anti-enumeration)")
    void forgotPassword_nonExistentEmail_returns200() throws Exception {
        String requestJson = """
                {
                    "email": "nobody@example.com"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType("application/json")
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Verify no reset token was created
        assertThat(passwordResetTokenRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("Forgot password response is identical for existing and non-existent email")
    void forgotPassword_responseIdentical_forExistingAndNonExistent() throws Exception {
        String existingEmailJson = """
                {"email": "reset-test@example.com"}
                """;
        String nonExistentEmailJson = """
                {"email": "nobody@example.com"}
                """;

        MvcResult existingResult = mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType("application/json")
                        .content(existingEmailJson))
                .andExpect(status().isOk())
                .andReturn();

        rateLimiter.clearAll();

        MvcResult nonExistentResult = mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType("application/json")
                        .content(nonExistentEmailJson))
                .andExpect(status().isOk())
                .andReturn();

        // Both responses should have the same structure and message
        JsonNode existingNode = objectMapper.readTree(existingResult.getResponse().getContentAsString());
        JsonNode nonExistentNode = objectMapper.readTree(nonExistentResult.getResponse().getContentAsString());

        assertThat(existingNode.get("success").asBoolean()).isEqualTo(nonExistentNode.get("success").asBoolean());
        assertThat(existingNode.get("message").asText()).isEqualTo(nonExistentNode.get("message").asText());
    }

    @Test
    @DisplayName("Forgot password normalizes email case")
    void forgotPassword_upperCaseEmail_createsToken() throws Exception {
        String requestJson = """
                {
                    "email": "RESET-TEST@EXAMPLE.COM"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType("application/json")
                        .content(requestJson))
                .andExpect(status().isOk());

        assertThat(passwordResetTokenRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("Forgot password enforces rate limiting")
    void forgotPassword_rateLimitExceeded_returns429() throws Exception {
        String requestJson = """
                {"email": "reset-test@example.com"}
                """;

        // 3 requests are allowed
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType("application/json")
                        .content(requestJson))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType("application/json")
                        .content(requestJson))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType("application/json")
                        .content(requestJson))
                .andExpect(status().isOk());

        // 4th request should be rate-limited
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType("application/json")
                        .content(requestJson))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("Forgot password rejects invalid email format")
    void forgotPassword_invalidEmail_returns400() throws Exception {
        String requestJson = """
                {"email": "not-an-email"}
                """;

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType("application/json")
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Forgot password rejects blank email")
    void forgotPassword_blankEmail_returns400() throws Exception {
        String requestJson = """
                {"email": ""}
                """;

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType("application/json")
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    // ==================== Reset Password Tests ====================

    @Test
    @DisplayName("Reset password succeeds with valid token")
    void resetPassword_validToken_returns200() throws Exception {
        // Generate and store a reset token
        String rawToken = tokenService.generateRawToken();
        String tokenHash = tokenService.hashToken(rawToken);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        passwordResetTokenRepository.save(resetToken);
        passwordResetTokenRepository.flush();

        String requestJson = String.format(
                "{\"token\": \"%s\", \"newPassword\": \"NewPassword@456\"}", rawToken
        );

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType("application/json")
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Verify password was changed
        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("NewPassword@456", updatedUser.getPasswordHash())).isTrue();

        // Verify token was consumed
        PasswordResetToken consumedToken = passwordResetTokenRepository.findByTokenHash(tokenHash).orElseThrow();
        assertThat(consumedToken.isConsumed()).isTrue();
        assertThat(consumedToken.getConsumedAt()).isNotNull();
    }

    @Test
    @DisplayName("Reset password allows login with new password")
    void resetPassword_canLoginWithNewPassword() throws Exception {
        // Generate and store a reset token
        String rawToken = tokenService.generateRawToken();
        String tokenHash = tokenService.hashToken(rawToken);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        passwordResetTokenRepository.save(resetToken);
        passwordResetTokenRepository.flush();

        // Reset password
        String resetJson = String.format(
                "{\"token\": \"%s\", \"newPassword\": \"NewPassword@456\"}", rawToken
        );
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType("application/json")
                        .content(resetJson))
                .andExpect(status().isOk());

        // Login with new password should succeed
        String loginJson = """
                {
                    "email": "reset-test@example.com",
                    "password": "NewPassword@456"
                }
                """;
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(loginJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

        // Login with old password should fail
        String oldLoginJson = """
                {
                    "email": "reset-test@example.com",
                    "password": "OldPassword@123"
                }
                """;
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(oldLoginJson))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Reset password rejects already consumed token")
    void resetPassword_consumedToken_returns400() throws Exception {
        String rawToken = tokenService.generateRawToken();
        String tokenHash = tokenService.hashToken(rawToken);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plusSeconds(3600))
                .consumedAt(Instant.now())
                .build();
        passwordResetTokenRepository.save(resetToken);
        passwordResetTokenRepository.flush();

        String requestJson = String.format(
                "{\"token\": \"%s\", \"newPassword\": \"NewPassword@456\"}", rawToken
        );

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType("application/json")
                        .content(requestJson))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("Reset password rejects expired token")
    void resetPassword_expiredToken_returns401() throws Exception {
        String rawToken = tokenService.generateRawToken();
        String tokenHash = tokenService.hashToken(rawToken);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().minusSeconds(3600))
                .build();
        passwordResetTokenRepository.save(resetToken);
        passwordResetTokenRepository.flush();

        String requestJson = String.format(
                "{\"token\": \"%s\", \"newPassword\": \"NewPassword@456\"}", rawToken
        );

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType("application/json")
                        .content(requestJson))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("TOKEN_EXPIRED"));
    }

    @Test
    @DisplayName("Reset password rejects unknown token")
    void resetPassword_unknownToken_returns400() throws Exception {
        String requestJson = """
                {"token": "unknown-token-value", "newPassword": "NewPassword@456"}
                """;

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType("application/json")
                        .content(requestJson))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("Reset password rejects blank token")
    void resetPassword_blankToken_returns400() throws Exception {
        String requestJson = """
                {"token": "", "newPassword": "NewPassword@456"}
                """;

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType("application/json")
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Reset password validates new password format")
    void resetPassword_weakPassword_returns400() throws Exception {
        String requestJson = """
                {"token": "some-token", "newPassword": "weak"}
                """;

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType("application/json")
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Reset password revokes all existing refresh tokens")
    void resetPassword_revokesRefreshTokens() throws Exception {
        // Create an existing refresh token for the user
        String rawRefreshToken = tokenService.generateRawToken();
        String refreshTokenHash = tokenService.hashToken(rawRefreshToken);

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(refreshTokenHash)
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);

        // Generate and store a password reset token
        String rawResetToken = tokenService.generateRawToken();
        String resetTokenHash = tokenService.hashToken(rawResetToken);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .tokenHash(resetTokenHash)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        passwordResetTokenRepository.save(resetToken);
        passwordResetTokenRepository.flush();

        String requestJson = String.format(
                "{\"token\": \"%s\", \"newPassword\": \"NewPassword@456\"}", rawResetToken
        );
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType("application/json")
                        .content(requestJson))
                .andExpect(status().isOk());

        // Verify refresh token was revoked
        RefreshToken updatedRefreshToken = refreshTokenRepository.findById(refreshToken.getId()).orElseThrow();
        assertThat(updatedRefreshToken.isRevoked()).isTrue();
    }

    @Test
    @DisplayName("Reset password token is single-use")
    void resetPassword_tokenCanBeUsedOnlyOnce() throws Exception {
        // Generate and store a reset token
        String rawToken = tokenService.generateRawToken();
        String tokenHash = tokenService.hashToken(rawToken);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        passwordResetTokenRepository.save(resetToken);
        passwordResetTokenRepository.flush();

        String requestJson = String.format(
                "{\"token\": \"%s\", \"newPassword\": \"NewPassword@456\"}", rawToken
        );

        // First use should succeed
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType("application/json")
                        .content(requestJson))
                .andExpect(status().isOk());

        // Second use should fail
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType("application/json")
                        .content(requestJson))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("New forgot password request invalidates previous reset token")
    void forgotPassword_newRequest_invalidatesPreviousToken() throws Exception {
        // First request
        String requestJson = """
                {"email": "reset-test@example.com"}
                """;
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType("application/json")
                        .content(requestJson))
                .andExpect(status().isOk());

        String firstTokenHash = passwordResetTokenRepository.findAll().get(0).getTokenHash();

        // Second request
        rateLimiter.clearAll();
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType("application/json")
                        .content(requestJson))
                .andExpect(status().isOk());

        // Only one token should exist (old one was deleted)
        assertThat(passwordResetTokenRepository.findAll()).hasSize(1);
        String secondTokenHash = passwordResetTokenRepository.findAll().get(0).getTokenHash();
        assertThat(secondTokenHash).isNotEqualTo(firstTokenHash);
    }

    // ==================== GET Reset Password (Email Link) Tests ====================

    @Test
    @DisplayName("GET reset-password validates valid token and returns 200")
    void getResetPassword_validToken_returns200() throws Exception {
        // Generate and store a reset token
        String rawToken = tokenService.generateRawToken();
        String tokenHash = tokenService.hashToken(rawToken);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        passwordResetTokenRepository.save(resetToken);
        passwordResetTokenRepository.flush();

        mockMvc.perform(get("/api/v1/auth/reset-password")
                        .param("token", rawToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Verify token was NOT consumed (GET should not consume the token)
        PasswordResetToken savedToken = passwordResetTokenRepository.findByTokenHash(tokenHash).orElseThrow();
        assertThat(savedToken.isConsumed()).isFalse();
    }

    @Test
    @DisplayName("GET reset-password rejects expired token")
    void getResetPassword_expiredToken_returns401() throws Exception {
        String rawToken = tokenService.generateRawToken();
        String tokenHash = tokenService.hashToken(rawToken);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().minusSeconds(3600))
                .build();
        passwordResetTokenRepository.save(resetToken);
        passwordResetTokenRepository.flush();

        mockMvc.perform(get("/api/v1/auth/reset-password")
                        .param("token", rawToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("TOKEN_EXPIRED"));
    }

    @Test
    @DisplayName("GET reset-password rejects already consumed token")
    void getResetPassword_consumedToken_returns401() throws Exception {
        String rawToken = tokenService.generateRawToken();
        String tokenHash = tokenService.hashToken(rawToken);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plusSeconds(3600))
                .consumedAt(Instant.now())
                .build();
        passwordResetTokenRepository.save(resetToken);
        passwordResetTokenRepository.flush();

        mockMvc.perform(get("/api/v1/auth/reset-password")
                        .param("token", rawToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("GET reset-password rejects unknown token")
    void getResetPassword_unknownToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/reset-password")
                        .param("token", "unknown-token-value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("GET reset-password allows subsequent POST reset-password with same token")
    void getResetPassword_thenPostResetPassword_works() throws Exception {
        // Generate and store a reset token
        String rawToken = tokenService.generateRawToken();
        String tokenHash = tokenService.hashToken(rawToken);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        passwordResetTokenRepository.save(resetToken);
        passwordResetTokenRepository.flush();

        // First, validate via GET (simulating clicking the email link)
        mockMvc.perform(get("/api/v1/auth/reset-password")
                        .param("token", rawToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Then, reset password via POST
        String requestJson = String.format(
                "{\"token\": \"%s\", \"newPassword\": \"NewPassword@456\"}", rawToken
        );
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType("application/json")
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Verify password was changed
        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("NewPassword@456", updatedUser.getPasswordHash())).isTrue();
    }
}
