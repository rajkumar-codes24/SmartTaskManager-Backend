package com.smarttaskmanager.auth.service;

import com.smarttaskmanager.auth.dto.PasswordResetRequest;
import com.smarttaskmanager.auth.dto.ResetPasswordConfirmation;
import com.smarttaskmanager.auth.entity.PasswordResetToken;
import com.smarttaskmanager.auth.entity.RefreshToken;
import com.smarttaskmanager.auth.repository.PasswordResetTokenRepository;
import com.smarttaskmanager.auth.repository.RefreshTokenRepository;
import com.smarttaskmanager.common.config.ApplicationProperties;
import com.smarttaskmanager.common.exception.InvalidTokenException;
import com.smarttaskmanager.common.exception.RateLimitExceededException;
import com.smarttaskmanager.common.ratelimit.InMemoryRateLimiter;
import com.smarttaskmanager.email.event.SendPasswordResetEmailEvent;
import com.smarttaskmanager.user.entity.Role;
import com.smarttaskmanager.user.entity.User;
import com.smarttaskmanager.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServicePasswordResetUnitTests {

    @Mock
    private UserRepository userRepository;

    @Mock
    private com.smarttaskmanager.auth.repository.EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Spy
    private TokenService tokenService;

    @Mock
    private ApplicationProperties properties;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Spy
    private InMemoryRateLimiter rateLimiter;

    @InjectMocks
    private AuthService authService;

    @Mock
    private ApplicationProperties.Jwt jwtProperties;

    private User user;

    @BeforeEach
    void setUp() {
        rateLimiter.clearAll();

        user = User.builder()
                .id(1L)
                .fullName("Alice Smith")
                .email("alice@example.com")
                .passwordHash("$2a$12$hashedPassword")
                .role(Role.ROLE_USER)
                .emailVerified(true)
                .enabled(true)
                .build();

        lenient().when(properties.jwt()).thenReturn(jwtProperties);
        lenient().when(jwtProperties.passwordResetTokenExpirationMs()).thenReturn(3600000L);
    }

    // ==================== Request Password Reset Tests ====================

    @Test
    @DisplayName("Request password reset generates token and publishes email event for existing user")
    void requestPasswordReset_existingUser_publishesEvent() {
        PasswordResetRequest request = new PasswordResetRequest("alice@example.com");

        when(userRepository.findByEmailIgnoreCase("alice@example.com")).thenReturn(Optional.of(user));

        authService.requestPasswordReset(request);

        verify(passwordResetTokenRepository).deleteAllByUser(user);
        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));

        ArgumentCaptor<SendPasswordResetEmailEvent> eventCaptor = ArgumentCaptor.forClass(SendPasswordResetEmailEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        SendPasswordResetEmailEvent event = eventCaptor.getValue();
        assertThat(event.userId()).isEqualTo(1L);
        assertThat(event.email()).isEqualTo("alice@example.com");
        assertThat(event.fullName()).isEqualTo("Alice Smith");
        assertThat(event.rawToken()).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("Request password reset returns silently for non-existent email (anti-enumeration)")
    void requestPasswordReset_nonExistentEmail_noEventPublished() {
        PasswordResetRequest request = new PasswordResetRequest("nobody@example.com");

        when(userRepository.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());

        authService.requestPasswordReset(request);

        verify(passwordResetTokenRepository, never()).save(any(PasswordResetToken.class));
        verify(eventPublisher, never()).publishEvent(any(SendPasswordResetEmailEvent.class));
    }

    @Test
    @DisplayName("Request password reset normalizes email case")
    void requestPasswordReset_upperCaseEmail_normalizesToLower() {
        PasswordResetRequest request = new PasswordResetRequest("ALICE@EXAMPLE.COM");

        when(userRepository.findByEmailIgnoreCase("alice@example.com")).thenReturn(Optional.of(user));

        authService.requestPasswordReset(request);

        verify(userRepository).findByEmailIgnoreCase("alice@example.com");
        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
    }

    @Test
    @DisplayName("Request password reset enforces rate limiting")
    void requestPasswordReset_rateLimitExceeded_throwsException() {
        PasswordResetRequest request = new PasswordResetRequest("alice@example.com");

        when(userRepository.findByEmailIgnoreCase("alice@example.com")).thenReturn(Optional.of(user));

        // Exhaust rate limit: 3 requests per 10 minutes
        authService.requestPasswordReset(request);
        authService.requestPasswordReset(request);
        authService.requestPasswordReset(request);

        assertThatThrownBy(() -> authService.requestPasswordReset(request))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("Too many password reset requests");
    }

    @Test
    @DisplayName("Request password reset creates token with correct expiration")
    void requestPasswordReset_createsTokenWithExpiration() {
        PasswordResetRequest request = new PasswordResetRequest("alice@example.com");

        when(userRepository.findByEmailIgnoreCase("alice@example.com")).thenReturn(Optional.of(user));

        authService.requestPasswordReset(request);

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(tokenCaptor.capture());
        PasswordResetToken savedToken = tokenCaptor.getValue();

        assertThat(savedToken.getUser().getId()).isEqualTo(1L);
        assertThat(savedToken.getTokenHash()).isNotNull().isNotEmpty();
        assertThat(savedToken.getExpiresAt()).isAfter(Instant.now());
        assertThat(savedToken.isConsumed()).isFalse();
    }

    // ==================== Confirm Password Reset Tests ====================

    @Test
    @DisplayName("Confirm password reset updates password and consumes token")
    void confirmPasswordReset_validToken_updatesPassword() {
        String rawToken = tokenService.generateRawToken();
        String tokenHash = tokenService.hashToken(rawToken);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .id(1L)
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        ResetPasswordConfirmation request = new ResetPasswordConfirmation(rawToken, "NewPassword@456");

        when(passwordResetTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(resetToken));
        when(passwordEncoder.encode("NewPassword@456")).thenReturn("$2a$12$newHashedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordResetTokenRepository.save(any(PasswordResetToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(refreshTokenRepository.findByUser(user)).thenReturn(List.of());

        authService.confirmPasswordReset(request);

        // Verify password was encoded
        verify(passwordEncoder).encode("NewPassword@456");

        // Verify user was saved with new password
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getPasswordHash()).isEqualTo("$2a$12$newHashedPassword");

        // Verify token was consumed
        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(tokenCaptor.capture());
        PasswordResetToken savedToken = tokenCaptor.getValue();
        assertThat(savedToken.isConsumed()).isTrue();
        assertThat(savedToken.getConsumedAt()).isNotNull();
    }

    @Test
    @DisplayName("Confirm password reset rejects already consumed token")
    void confirmPasswordReset_consumedToken_throwsInvalidTokenException() {
        String rawToken = tokenService.generateRawToken();
        String tokenHash = tokenService.hashToken(rawToken);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .id(1L)
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plusSeconds(3600))
                .consumedAt(Instant.now())
                .build();

        ResetPasswordConfirmation request = new ResetPasswordConfirmation(rawToken, "NewPassword@456");

        when(passwordResetTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(resetToken));

        assertThatThrownBy(() -> authService.confirmPasswordReset(request))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("already been used");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Confirm password reset rejects expired token")
    void confirmPasswordReset_expiredToken_throwsInvalidTokenException() {
        String rawToken = tokenService.generateRawToken();
        String tokenHash = tokenService.hashToken(rawToken);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .id(1L)
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().minusSeconds(3600))
                .build();

        ResetPasswordConfirmation request = new ResetPasswordConfirmation(rawToken, "NewPassword@456");

        when(passwordResetTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(resetToken));

        assertThatThrownBy(() -> authService.confirmPasswordReset(request))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("expired");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Confirm password reset rejects unknown token")
    void confirmPasswordReset_unknownToken_throwsInvalidTokenException() {
        ResetPasswordConfirmation request = new ResetPasswordConfirmation("unknown-token", "NewPassword@456");

        when(passwordResetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.confirmPasswordReset(request))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("Invalid or expired");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Confirm password reset rejects blank token")
    void confirmPasswordReset_blankToken_throwsInvalidTokenException() {
        ResetPasswordConfirmation request = new ResetPasswordConfirmation("", "NewPassword@456");

        assertThatThrownBy(() -> authService.confirmPasswordReset(request))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    @DisplayName("Confirm password reset revokes all existing refresh tokens")
    void confirmPasswordReset_revokesRefreshTokens() {
        String rawToken = tokenService.generateRawToken();
        String tokenHash = tokenService.hashToken(rawToken);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .id(1L)
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        RefreshToken refreshToken1 = RefreshToken.builder()
                .id(1L)
                .user(user)
                .tokenHash("hash1")
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();
        RefreshToken refreshToken2 = RefreshToken.builder()
                .id(2L)
                .user(user)
                .tokenHash("hash2")
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();

        ResetPasswordConfirmation request = new ResetPasswordConfirmation(rawToken, "NewPassword@456");

        when(passwordResetTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(resetToken));
        when(passwordEncoder.encode("NewPassword@456")).thenReturn("$2a$12$newHashedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordResetTokenRepository.save(any(PasswordResetToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(refreshTokenRepository.findByUser(user)).thenReturn(List.of(refreshToken1, refreshToken2));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.confirmPasswordReset(request);

        // Verify both refresh tokens were revoked
        assertThat(refreshToken1.isRevoked()).isTrue();
        assertThat(refreshToken2.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(refreshToken1);
        verify(refreshTokenRepository).save(refreshToken2);
    }

    @Test
    @DisplayName("Confirm password reset skips already revoked refresh tokens")
    void confirmPasswordReset_skipsAlreadyRevokedRefreshTokens() {
        String rawToken = tokenService.generateRawToken();
        String tokenHash = tokenService.hashToken(rawToken);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .id(1L)
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        RefreshToken revokedToken = RefreshToken.builder()
                .id(1L)
                .user(user)
                .tokenHash("hash1")
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(true)
                .build();

        ResetPasswordConfirmation request = new ResetPasswordConfirmation(rawToken, "NewPassword@456");

        when(passwordResetTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(resetToken));
        when(passwordEncoder.encode("NewPassword@456")).thenReturn("$2a$12$newHashedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordResetTokenRepository.save(any(PasswordResetToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(refreshTokenRepository.findByUser(user)).thenReturn(List.of(revokedToken));

        authService.confirmPasswordReset(request);

        // Already revoked token should not be saved again
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }
}
