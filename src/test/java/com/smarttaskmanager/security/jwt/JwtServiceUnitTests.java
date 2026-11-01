package com.smarttaskmanager.security.jwt;

import com.smarttaskmanager.common.config.ApplicationProperties;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtServiceUnitTests {

    private static final String TEST_SECRET = "4c696e676572696e675f73757065725f7365637265745f6b65795f666f725f736d6172745f7461736b5f6d616e616765725f3230323621";

    @Mock
    private ApplicationProperties properties;

    @Mock
    private ApplicationProperties.Jwt jwtProperties;

    private JwtService jwtService;

    private final org.springframework.security.core.userdetails.User testUser =
            new org.springframework.security.core.userdetails.User(
                    "alice@example.com",
                    "hashedPassword",
                    List.of(new SimpleGrantedAuthority("ROLE_USER"))
            );

    @BeforeEach
    void setUp() {
        when(properties.jwt()).thenReturn(jwtProperties);
        when(jwtProperties.secret()).thenReturn(TEST_SECRET);
        when(jwtProperties.accessTokenExpirationMs()).thenReturn(900000L); // 15 minutes
        jwtService = new JwtService(properties);
    }

    @Test
    @DisplayName("Generate access token creates a valid JWT")
    void generateAccessToken_createsValidToken() {
        String token = jwtService.generateAccessToken(testUser);

        assertThat(token).isNotNull().isNotEmpty();
        assertThat(token).contains("."); // JWT has 3 parts separated by dots

        String email = jwtService.extractEmail(token);
        assertThat(email).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("Generated token contains the correct subject (email)")
    void generateAccessToken_containsCorrectEmail() {
        String token = jwtService.generateAccessToken(testUser);

        String email = jwtService.extractEmail(token);
        assertThat(email).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("Token validation returns true for valid token and matching user")
    void isTokenValid_validToken_returnsTrue() {
        String token = jwtService.generateAccessToken(testUser);

        assertThat(jwtService.isTokenValid(token, testUser)).isTrue();
    }

    @Test
    @DisplayName("Token validation returns false when email does not match user")
    void isTokenValid_emailMismatch_returnsFalse() {
        String token = jwtService.generateAccessToken(testUser);

        org.springframework.security.core.userdetails.User otherUser =
                new org.springframework.security.core.userdetails.User(
                        "bob@example.com",
                        "hashedPassword",
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                );

        assertThat(jwtService.isTokenValid(token, otherUser)).isFalse();
    }

    @Test
    @DisplayName("Token validation returns false for tampered token")
    void isTokenValid_tamperedToken_returnsFalse() {
        String token = jwtService.generateAccessToken(testUser);
        String tamperedToken = token.substring(0, token.length() - 2) + "XX";

        assertThat(jwtService.isTokenValid(tamperedToken, testUser)).isFalse();
    }

    @Test
    @DisplayName("Expired token is detected correctly")
    void isTokenExpired_expiredToken_returnsTrue() {
        // Generate a token with 1ms expiration
        String token = jwtService.generateTokenWithExpiration(
                java.util.Map.of(), "alice@example.com", 1L
        );

        // Wait for token to expire
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // isTokenExpired internally calls extractAllClaims which throws JwtException for expired tokens
        // The isTokenValid method catches this and returns false, which is the correct behavior
        assertThat(jwtService.isTokenValid(token, testUser)).isFalse();
    }

    @Test
    @DisplayName("Fresh token is not expired")
    void isTokenExpired_freshToken_returnsFalse() {
        String token = jwtService.generateAccessToken(testUser);

        assertThat(jwtService.isTokenExpired(token)).isFalse();
    }

    @Test
    @DisplayName("Extracting email from invalid token throws exception")
    void extractEmail_invalidToken_throwsJwtException() {
        assertThatThrownBy(() -> jwtService.extractEmail("invalid.token.here"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Extracting email from empty string throws exception")
    void extractEmail_emptyString_throwsException() {
        assertThatThrownBy(() -> jwtService.extractEmail(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null or empty");
    }

    @Test
    @DisplayName("Token with custom expiration is generated correctly")
    void generateTokenWithExpiration_customExpiration_works() {
        long customExpirationMs = 3600000L; // 1 hour
        Map<String, Object> extraClaims = Map.of("custom", "value");

        String token = jwtService.generateTokenWithExpiration(extraClaims, "custom@example.com", customExpirationMs);

        assertThat(token).isNotNull().isNotEmpty();
        assertThat(jwtService.extractEmail(token)).isEqualTo("custom@example.com");
        assertThat(jwtService.isTokenExpired(token)).isFalse();
    }

    @Test
    @DisplayName("Constructor throws IllegalStateException when secret is null")
    void constructor_nullSecret_throwsIllegalStateException() {
        when(properties.jwt()).thenReturn(jwtProperties);
        when(jwtProperties.secret()).thenReturn(null);

        assertThatThrownBy(() -> new JwtService(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT secret must be configured");
    }

    @Test
    @DisplayName("Constructor throws IllegalStateException when secret is blank")
    void constructor_blankSecret_throwsIllegalStateException() {
        when(properties.jwt()).thenReturn(jwtProperties);
        when(jwtProperties.secret()).thenReturn("   ");

        assertThatThrownBy(() -> new JwtService(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT secret must be configured");
    }

    @Test
    @DisplayName("Token validation returns false for completely invalid string")
    void isTokenValid_garbageString_returnsFalse() {
        assertThat(jwtService.isTokenValid("not-a-jwt-at-all", testUser)).isFalse();
    }
}
