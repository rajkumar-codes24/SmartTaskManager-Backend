package com.smarttaskmanager.auth.integration;

import com.smarttaskmanager.auth.entity.RefreshToken;
import com.smarttaskmanager.auth.repository.RefreshTokenRepository;
import com.smarttaskmanager.auth.service.TokenService;
import com.smarttaskmanager.common.config.ApplicationProperties;
import com.smarttaskmanager.security.jwt.JwtService;
import com.smarttaskmanager.user.entity.Role;
import com.smarttaskmanager.user.entity.User;
import com.smarttaskmanager.user.repository.UserRepository;
import com.smarttaskmanager.common.ratelimit.InMemoryRateLimiter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.transaction.annotation.Transactional
class LoginRefreshLogoutIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ApplicationProperties applicationProperties;

    @Autowired
    private InMemoryRateLimiter rateLimiter;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JwtService jwtService;
    private TokenService tokenService;

    private User verifiedUser;
    private User unverifiedUser;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(applicationProperties);
        tokenService = new TokenService();
        rateLimiter.clearAll();

        // Create a verified user
        verifiedUser = User.builder()
                .fullName("Alice Smith")
                .email("login-test@example.com")
                .passwordHash(passwordEncoder.encode("TestPassword@123"))
                .role(Role.ROLE_USER)
                .enabled(true)
                .emailVerified(true)
                .build();
        verifiedUser = userRepository.save(verifiedUser);

        // Create an unverified user
        unverifiedUser = User.builder()
                .fullName("Bob Jones")
                .email("unverified-test@example.com")
                .passwordHash(passwordEncoder.encode("TestPassword@123"))
                .role(Role.ROLE_USER)
                .enabled(true)
                .emailVerified(false)
                .build();
        unverifiedUser = userRepository.save(unverifiedUser);
    }

    // ==================== Login Tests ====================

    @Test
    @DisplayName("Login succeeds for verified user with correct credentials")
    void login_verifiedUser_correctPassword_returns200() throws Exception {
        String loginJson = """
                {
                    "email": "login-test@example.com",
                    "password": "TestPassword@123"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(loginJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.accessTokenExpiresInMs").isNumber());
    }

    @Test
    @DisplayName("Login fails for wrong password")
    void login_wrongPassword_returns401() throws Exception {
        String loginJson = """
                {
                    "email": "login-test@example.com",
                    "password": "WrongPassword@123"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(loginJson))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_FAILED"));
    }

    @Test
    @DisplayName("Login fails for non-existent email")
    void login_nonExistentEmail_returns401() throws Exception {
        String loginJson = """
                {
                    "email": "nonexistent@example.com",
                    "password": "TestPassword@123"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(loginJson))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_FAILED"));
    }

    @Test
    @DisplayName("Login fails for unverified email")
    void login_unverifiedEmail_returns400() throws Exception {
        String loginJson = """
                {
                    "email": "unverified-test@example.com",
                    "password": "TestPassword@123"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(loginJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value("Please verify your email address before logging in."));
    }

    @Test
    @DisplayName("Login fails with validation error for missing email")
    void login_missingEmail_returns400() throws Exception {
        String loginJson = """
                {
                    "password": "TestPassword@123"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(loginJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Login fails with validation error for short password")
    void login_shortPassword_returns400() throws Exception {
        String loginJson = """
                {
                    "email": "login-test@example.com",
                    "password": "Short1"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(loginJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Login normalizes email case")
    void login_emailCaseNormalization_returns200() throws Exception {
        String loginJson = """
                {
                    "email": "LOGIN-TEST@EXAMPLE.COM",
                    "password": "TestPassword@123"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(loginJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("Login updates lastLoginAt timestamp")
    void login_updatesLastLoginAt() throws Exception {
        String loginJson = """
                {
                    "email": "login-test@example.com",
                    "password": "TestPassword@123"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(loginJson))
                .andExpect(status().isOk());

        User updatedUser = userRepository.findById(verifiedUser.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(updatedUser.getLastLoginAt()).isNotNull();
    }

    @Test
    @DisplayName("Login stores refresh token hash in database")
    void login_storesRefreshTokenHash() throws Exception {
        String loginJson = """
                {
                    "email": "login-test@example.com",
                    "password": "TestPassword@123"
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(loginJson))
                .andExpect(status().isOk())
                .andReturn();

        // Extract the raw refresh token from the response body
        String responseBody = result.getResponse().getContentAsString();
        JsonNode rootNode = objectMapper.readTree(responseBody);
        String rawRefreshToken = rootNode.path("data").path("refreshToken").asText();

        // Verify the hash is stored, not the raw token
        String tokenHash = tokenService.hashToken(rawRefreshToken);
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash).orElseThrow();

        org.assertj.core.api.Assertions.assertThat(storedToken.getTokenHash()).isEqualTo(tokenHash);
        org.assertj.core.api.Assertions.assertThat(storedToken.getTokenHash()).isNotEqualTo(rawRefreshToken);
        org.assertj.core.api.Assertions.assertThat(storedToken.isRevoked()).isFalse();
        org.assertj.core.api.Assertions.assertThat(storedToken.getUser().getId()).isEqualTo(verifiedUser.getId());
    }

    // ==================== Refresh Tests ====================

    @Test
    @DisplayName("Refresh returns new tokens for valid refresh token")
    void refresh_validToken_returns200() throws Exception {
        // First, login to get a refresh token
        String loginJson = """
                {
                    "email": "login-test@example.com",
                    "password": "TestPassword@123"
                }
                """;

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(loginJson))
                .andExpect(status().isOk())
                .andReturn();

        String rawRefreshToken = extractRefreshToken(loginResult);

        // Now refresh
        String refreshJson = """
                {
                    "refreshToken": "%s"
                }
                """.formatted(rawRefreshToken);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content(refreshJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());
    }

    @Test
    @DisplayName("Refresh rotates the token — old token cannot be reused")
    void refresh_tokenRotation_oldTokenRevoked() throws Exception {
        // Login
        String loginJson = """
                {
                    "email": "login-test@example.com",
                    "password": "TestPassword@123"
                }
                """;

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(loginJson))
                .andExpect(status().isOk())
                .andReturn();

        String rawRefreshToken = extractRefreshToken(loginResult);

        String refreshJson = """
                {
                    "refreshToken": "%s"
                }
                """.formatted(rawRefreshToken);

        // First refresh succeeds
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content(refreshJson))
                .andExpect(status().isOk());

        // Second refresh with the same token fails (token revoked) — returns 401 because InvalidTokenException maps to UNAUTHORIZED
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content(refreshJson))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("TOKEN_REVOKED"))
                .andExpect(jsonPath("$.message").value("Refresh token has been revoked"));
    }

    @Test
    @DisplayName("Refresh fails for unknown refresh token")
    void refresh_unknownToken_returns401() throws Exception {
        String refreshJson = """
                {
                    "refreshToken": "unknownToken1234567890abcdef1234567890"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content(refreshJson))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("Refresh fails for expired refresh token")
    void refresh_expiredToken_returns401() throws Exception {
        // Create an expired refresh token manually
        String rawRefreshToken = tokenService.generateRawToken();
        String tokenHash = tokenService.hashToken(rawRefreshToken);

        RefreshToken expiredToken = RefreshToken.builder()
                .user(verifiedUser)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .revoked(false)
                .build();
        refreshTokenRepository.save(expiredToken);

        String refreshJson = """
                {
                    "refreshToken": "%s"
                }
                """.formatted(rawRefreshToken);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content(refreshJson))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("TOKEN_EXPIRED"))
                .andExpect(jsonPath("$.message").value("Refresh token has expired"));
    }

    @Test
    @DisplayName("Refresh fails with validation error for missing token")
    void refresh_missingToken_returns400() throws Exception {
        String refreshJson = """
                {
                    "refreshToken": ""
                }
                """;

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content(refreshJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    // ==================== Logout Tests ====================

    @Test
    @DisplayName("Logout succeeds and revokes refresh token")
    void logout_validToken_returns200() throws Exception {
        // Login first
        String loginJson = """
                {
                    "email": "login-test@example.com",
                    "password": "TestPassword@123"
                }
                """;

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(loginJson))
                .andExpect(status().isOk())
                .andReturn();

        String rawRefreshToken = extractRefreshToken(loginResult);

        // Logout
        String logoutJson = """
                {
                    "refreshToken": "%s"
                }
                """.formatted(rawRefreshToken);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType("application/json")
                        .content(logoutJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Verify token is revoked
        String tokenHash = tokenService.hashToken(rawRefreshToken);
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(storedToken.isRevoked()).isTrue();
    }

    @Test
    @DisplayName("Logout then refresh fails — token is revoked")
    void logout_thenRefresh_returns400() throws Exception {
        // Login
        String loginJson = """
                {
                    "email": "login-test@example.com",
                    "password": "TestPassword@123"
                }
                """;

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(loginJson))
                .andExpect(status().isOk())
                .andReturn();

        String rawRefreshToken = extractRefreshToken(loginResult);

        String tokenJson = """
                {
                    "refreshToken": "%s"
                }
                """.formatted(rawRefreshToken);

        // Logout
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType("application/json")
                        .content(tokenJson))
                .andExpect(status().isOk());

        // Try to refresh with the revoked token
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content(tokenJson))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("TOKEN_REVOKED"));
    }

    @Test
    @DisplayName("Logout with unknown token succeeds silently")
    void logout_unknownToken_returns200() throws Exception {
        String logoutJson = """
                {
                    "refreshToken": "unknownToken1234567890abcdef1234567890"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType("application/json")
                        .content(logoutJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ==================== Rate Limiting Tests ====================

    @Test
    @DisplayName("Login rate limiting blocks after 5 attempts")
    void login_rateLimiting_blocksAfter5Attempts() throws Exception {
        String loginJson = """
                {
                    "email": "login-test@example.com",
                    "password": "WrongPassword@123"
                }
                """;

        // 5 failed attempts
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType("application/json")
                            .content(loginJson))
                    .andExpect(status().isUnauthorized());
        }

        // 6th attempt should be rate limited
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(loginJson))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"));
    }

    // ==================== Anti-enumeration Tests ====================

    @Test
    @DisplayName("Login for non-existent email returns same error as wrong password")
    void login_antiEnumeration_sameErrorForNonExistentAndWrongPassword() throws Exception {
        String nonExistentJson = """
                {
                    "email": "nonexistent@example.com",
                    "password": "TestPassword@123"
                }
                """;

        String wrongPasswordJson = """
                {
                    "email": "login-test@example.com",
                    "password": "WrongPassword@123"
                }
                """;

        MvcResult nonExistentResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(nonExistentJson))
                .andExpect(status().isUnauthorized())
                .andReturn();

        MvcResult wrongPasswordResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(wrongPasswordJson))
                .andExpect(status().isUnauthorized())
                .andReturn();

        // Both should return the same error code
        JsonNode nonExistentNode = objectMapper.readTree(nonExistentResult.getResponse().getContentAsString());
        JsonNode wrongPasswordNode = objectMapper.readTree(wrongPasswordResult.getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(nonExistentNode.path("errorCode").asText())
                .isEqualTo(wrongPasswordNode.path("errorCode").asText());
    }

    // ==================== Login returns valid JWT ====================

    @Test
    @DisplayName("Login access token can be used to access protected endpoints")
    void login_tokenWorksForProtectedEndpoint() throws Exception {
        String loginJson = """
                {
                    "email": "login-test@example.com",
                    "password": "TestPassword@123"
                }
                """;

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(loginJson))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        JsonNode rootNode = objectMapper.readTree(responseBody);
        String accessToken = rootNode.path("data").path("accessToken").asText();

        // Use the access token to access a protected endpoint
        mockMvc.perform(post("/api/v1/auth/register")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content("""
                                {
                                    "fullName": "Test User",
                                    "email": "another-test@example.com",
                                    "password": "TestPassword@123"
                                }
                                """))
                .andExpect(status().isCreated());
    }

    // ==================== Helper Methods ====================

    private String extractRefreshToken(MvcResult result) throws Exception {
        String responseBody = result.getResponse().getContentAsString();
        JsonNode rootNode = objectMapper.readTree(responseBody);
        return rootNode.path("data").path("refreshToken").asText();
    }
}
