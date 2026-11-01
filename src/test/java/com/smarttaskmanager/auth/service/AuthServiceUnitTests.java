package com.smarttaskmanager.auth.service;

import com.smarttaskmanager.auth.dto.RegisterRequest;
import com.smarttaskmanager.auth.dto.ResendVerificationRequest;
import com.smarttaskmanager.auth.entity.EmailVerificationToken;
import com.smarttaskmanager.auth.repository.EmailVerificationTokenRepository;
import com.smarttaskmanager.common.config.ApplicationProperties;
import com.smarttaskmanager.common.exception.DuplicateResourceException;
import com.smarttaskmanager.common.exception.InvalidTokenException;
import com.smarttaskmanager.common.exception.RateLimitExceededException;
import com.smarttaskmanager.common.ratelimit.InMemoryRateLimiter;
import com.smarttaskmanager.email.event.SendVerificationEmailEvent;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceUnitTests {

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

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

    @BeforeEach
    void setUp() {
        rateLimiter.clearAll();
    }

    @Test
    @DisplayName("Registration successfully persists user with hashed password and publishes event")
    void register_success() {
        RegisterRequest request = new RegisterRequest("Alice Smith", "alice@example.com", "Password@123");

        when(userRepository.existsByEmailIgnoreCase("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password@123")).thenReturn("$2a$12$hashedPassword");
        when(properties.jwt()).thenReturn(jwtProperties);
        when(jwtProperties.verificationTokenExpirationMs()).thenReturn(86400000L);

        User savedUser = User.builder()
                .id(1L)
                .fullName("Alice Smith")
                .email("alice@example.com")
                .passwordHash("$2a$12$hashedPassword")
                .role(Role.ROLE_USER)
                .emailVerified(false)
                .enabled(true)
                .build();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        authService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User capturedUser = userCaptor.getValue();
        assertThat(capturedUser.getEmail()).isEqualTo("alice@example.com");
        assertThat(capturedUser.isEmailVerified()).isFalse();
        assertThat(capturedUser.getPasswordHash()).isEqualTo("$2a$12$hashedPassword");

        verify(emailVerificationTokenRepository).save(any(EmailVerificationToken.class));
        verify(eventPublisher).publishEvent(any(SendVerificationEmailEvent.class));
    }

    @Test
    @DisplayName("Registration fails when email already exists")
    void register_duplicateEmail_throwsDuplicateResourceException() {
        RegisterRequest request = new RegisterRequest("Alice Smith", "alice@example.com", "Password@123");
        when(userRepository.existsByEmailIgnoreCase("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("alice@example.com");

        verify(userRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("Email verification succeeds with valid token and activates account")
    void verifyEmail_success() {
        String rawToken = "validRawToken1234567890abcdef123456";
        String tokenHash = tokenService.hashToken(rawToken);

        User user = User.builder()
                .id(1L)
                .email("alice@example.com")
                .emailVerified(false)
                .build();

        EmailVerificationToken token = EmailVerificationToken.builder()
                .id(10L)
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();

        when(emailVerificationTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        authService.verifyEmail(rawToken);

        assertThat(token.isConsumed()).isTrue();
        assertThat(user.isEmailVerified()).isTrue();
        verify(emailVerificationTokenRepository).save(token);
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("Email verification fails when token is not found")
    void verifyEmail_tokenNotFound_throwsInvalidTokenException() {
        when(emailVerificationTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyEmail("unknownToken"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("Invalid verification token");
    }

    @Test
    @DisplayName("Email verification fails when token was already used")
    void verifyEmail_tokenAlreadyConsumed_throwsInvalidTokenException() {
        String rawToken = "usedToken1234567890abcdef123456";
        String tokenHash = tokenService.hashToken(rawToken);

        EmailVerificationToken token = EmailVerificationToken.builder()
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .consumedAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .user(User.builder().id(1L).build())
                .build();

        when(emailVerificationTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.verifyEmail(rawToken))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("already been used");
    }

    @Test
    @DisplayName("Email verification fails when token has expired")
    void verifyEmail_tokenExpired_throwsInvalidTokenException() {
        String rawToken = "expiredToken1234567890abcdef123456";
        String tokenHash = tokenService.hashToken(rawToken);

        EmailVerificationToken token = EmailVerificationToken.builder()
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().minus(1, ChronoUnit.MINUTES))
                .user(User.builder().id(1L).build())
                .build();

        when(emailVerificationTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.verifyEmail(rawToken))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("Resend verification successfully sends link and deletes previous tokens")
    void resendVerification_success() {
        ResendVerificationRequest request = new ResendVerificationRequest("alice@example.com");
        User user = User.builder().id(1L).email("alice@example.com").fullName("Alice").emailVerified(false).build();

        when(userRepository.findByEmailIgnoreCase("alice@example.com")).thenReturn(Optional.of(user));
        when(properties.jwt()).thenReturn(jwtProperties);
        when(jwtProperties.verificationTokenExpirationMs()).thenReturn(86400000L);

        authService.resendVerification(request);

        verify(emailVerificationTokenRepository).deleteAllByUser(user);
        verify(emailVerificationTokenRepository).save(any(EmailVerificationToken.class));
        verify(eventPublisher).publishEvent(any(SendVerificationEmailEvent.class));
    }

    @Test
    @DisplayName("Resend verification enforces rate limiting")
    void resendVerification_rateLimitExceeded_throwsRateLimitExceededException() {
        ResendVerificationRequest request = new ResendVerificationRequest("alice@example.com");
        User user = User.builder().id(1L).email("alice@example.com").fullName("Alice").emailVerified(false).build();

        when(userRepository.findByEmailIgnoreCase("alice@example.com")).thenReturn(Optional.of(user));
        when(properties.jwt()).thenReturn(jwtProperties);
        when(jwtProperties.verificationTokenExpirationMs()).thenReturn(86400000L);

        // First attempt succeeds
        authService.resendVerification(request);

        // Immediate second attempt within 2-minute cooldown must throw RateLimitExceededException
        assertThatThrownBy(() -> authService.resendVerification(request))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("Verification email was sent recently");
    }

    @Test
    @DisplayName("Resend verification for non-existent email returns silently (anti-enumeration)")
    void resendVerification_nonExistentEmail_silentReturn() {
        ResendVerificationRequest request = new ResendVerificationRequest("ghost@example.com");
        when(userRepository.findByEmailIgnoreCase("ghost@example.com")).thenReturn(Optional.empty());

        authService.resendVerification(request);

        verify(emailVerificationTokenRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }
}
