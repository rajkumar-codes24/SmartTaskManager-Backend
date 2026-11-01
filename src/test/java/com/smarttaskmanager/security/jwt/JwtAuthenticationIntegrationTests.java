package com.smarttaskmanager.security.jwt;

import com.smarttaskmanager.common.exception.ErrorResponse;
import com.smarttaskmanager.common.config.ApplicationProperties;
import com.smarttaskmanager.user.entity.Role;
import com.smarttaskmanager.user.entity.User;
import com.smarttaskmanager.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(TestSecurityController.class)
@org.springframework.transaction.annotation.Transactional
class JwtAuthenticationIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ApplicationProperties applicationProperties;

    private JwtService jwtService;

    private User testUser;
    private String validToken;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(applicationProperties);

        // Create a test user in the H2 database
        testUser = User.builder()
                .fullName("Test User")
                .email("jwttest@example.com")
                .passwordHash(passwordEncoder.encode("TestPassword@123"))
                .role(Role.ROLE_USER)
                .enabled(true)
                .emailVerified(true)
                .lastLoginAt(Instant.now())
                .build();
        testUser = userRepository.save(testUser);

        // Generate a valid token for this user
        org.springframework.security.core.userdetails.User userDetails =
                new org.springframework.security.core.userdetails.User(
                        testUser.getEmail(),
                        testUser.getPasswordHash(),
                        java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(testUser.getRole().name()))
                );
        validToken = jwtService.generateAccessToken(userDetails);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        // Transactional rollback handles cleanup
    }

    @Test
    @DisplayName("Public endpoint is accessible without authentication")
    void publicEndpoint_noAuth_returns200() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Protected endpoint returns 401 without authentication")
    void protectedEndpoint_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/test/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("Protected endpoint returns 200 with valid JWT")
    void protectedEndpoint_validJwt_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/test/protected")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Protected endpoint returns 401 with invalid JWT")
    void protectedEndpoint_invalidJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/test/protected")
                        .header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("Protected endpoint returns 401 with expired JWT")
    void protectedEndpoint_expiredJwt_returns401() throws Exception {
        // Generate a token that expires immediately
        String expiredToken = jwtService.generateTokenWithExpiration(
                java.util.Map.of(), testUser.getEmail(), 1L
        );
        // Wait for expiration
        Thread.sleep(10);

        mockMvc.perform(get("/api/v1/test/protected")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("Protected endpoint returns 401 with tampered JWT")
    void protectedEndpoint_tamperedJwt_returns401() throws Exception {
        String tamperedToken = validToken.substring(0, validToken.length() - 2) + "XX";

        mockMvc.perform(get("/api/v1/test/protected")
                        .header("Authorization", "Bearer " + tamperedToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("Protected endpoint returns 401 with missing Bearer prefix")
    void protectedEndpoint_missingBearerPrefix_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/test/protected")
                        .header("Authorization", validToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("Protected endpoint returns 401 with empty Authorization header")
    void protectedEndpoint_emptyAuthHeader_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/test/protected")
                        .header("Authorization", ""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("Registration endpoint is accessible without authentication")
    void registerEndpoint_noAuth_returns201() throws Exception {
        String registerJson = """
                {
                    "fullName": "New User",
                    "email": "newuser@example.com",
                    "password": "SecurePass@123"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(registerJson))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Error response has correct structure")
    void unauthorizedError_hasCorrectStructure() throws Exception {
        mockMvc.perform(get("/api/v1/test/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value("/api/v1/test/protected"));
    }
}
