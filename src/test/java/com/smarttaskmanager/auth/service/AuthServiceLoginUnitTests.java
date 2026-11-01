package com.smarttaskmanager.auth.service;

import com.smarttaskmanager.auth.dto.LoginRequest;
import com.smarttaskmanager.auth.dto.LoginResponse;
import com.smarttaskmanager.auth.dto.RefreshResponse;
import com.smarttaskmanager.auth.entity.RefreshToken;
import com.smarttaskmanager.auth.repository.RefreshTokenRepository;
import com.smarttaskmanager.common.config.ApplicationProperties;
import com.smarttaskmanager.common.exception.BusinessRuleException;
import com.smarttaskmanager.common.exception.InvalidTokenException;
import com.smarttaskmanager.common.exception.RateLimitExceededException;
import com.smarttaskmanager.common.ratelimit.InMemoryRateLimiter;
import com.smarttaskmanager.security.jwt.JwtService;
import com.smarttaskmanager.security.user.UserDetailsServiceImpl;
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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
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
class AuthServiceLoginUnitTests {

    @Mock
    private UserRepository userRepository;

    @Mock
    private com.smarttaskmanager.auth.repository.EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Spy
    private TokenService tokenService;

    @Mock
    private JwtService jwtService;

    @Mock
    private UserDetailsServiceImpl userDetailsService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private ApplicationProperties properties;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Spy
    private InMemoryRateLimiter rateLimiter;

    @InjectMocks
    private AuthService authService;

    @Mock
    private ApplicationProperties.Jwt jwtProperties;

    private User verifiedUser;
    private User unverifiedUser;
    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        rateLimiter.clearAll();

        verifiedUser = User.builder()
                .id(1L)
                .fullName("Alice Smith")
                .email("alice@example.com")
                .passwordHash("$2a$12$hashedPassword")
                .role(Role.ROLE_USER)
                .emailVerified(true)
                .enabled(true)
                .build();

        unverifiedUser = User.builder()
                .id(2L)
                .fullName("Bob Jones")
                .email("bob@example.com")
                .passwordHash("$2a$12$hashedPassword")
                .role(Role.ROLE_USER)
                .emailVerified(false)
                .enabled(true)
                .build();

        userDetails = new org.springframework.security.core.userdetails.User(
                "alice@example.com",
                "$2a$12$hashedPassword",
                true,
                true,
                true,
                true,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        lenient().when(properties.jwt()).thenReturn(jwtProperties);
        lenient().when(jwtProperties.accessTokenExpirationMs()).thenReturn(900000L);
        lenient().when(jwtProperties.refreshTokenExpirationMs()).thenReturn(604800000L);
    }

    // ==================== Login Tests ====================

    @Test
    @DisplayName("Login succeeds for verified user and returns tokens")
    void login_verifiedUser_returnsTokens() {
        LoginRequest request = new LoginRequest("alice@example.com", "Password@123");

        Authentication auth = mock(Authentication.class);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(auth.getPrincipal()).thenReturn(userDetails);
        when(userRepository.findByEmailIgnoreCase("alice@example.com")).thenReturn(Optional.of(verifiedUser));
        when(jwtService.generateAccessToken(userDetails)).thenReturn("jwtAccessToken123");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LoginResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("jwtAccessToken123");
        assertThat(response.refreshToken()).isNotNull().isNotEmpty();
        assertThat(response.accessTokenExpiresInMs()).isEqualTo(900000L);

        verify(refreshTokenRepository).save(any(RefreshToken.class));
        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());
        RefreshToken savedToken = tokenCaptor.getValue();
        assertThat(savedToken.getUser().getId()).isEqualTo(1L);
        assertThat(savedToken.isRevoked()).isFalse();

        // Verify lastLoginAt was updated
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getLastLoginAt()).isNotNull();
    }

    @Test
    @DisplayName("Login fails with BadCredentialsException for wrong password")
    void login_wrongPassword_throwsBadCredentialsException() {
        LoginRequest request = new LoginRequest("alice@example.com", "WrongPassword@123");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);

        verify(jwtService, never()).generateAccessToken(any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Login fails for unverified email")
    void login_unverifiedEmail_throwsBusinessRuleException() {
        LoginRequest request = new LoginRequest("bob@example.com", "Password@123");

        UserDetails unverifiedUserDetails = new org.springframework.security.core.userdetails.User(
                "bob@example.com",
                "$2a$12$hashedPassword",
                true,
                true,
                true,
                true,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        Authentication auth = mock(Authentication.class);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(auth.getPrincipal()).thenReturn(unverifiedUserDetails);
        when(userRepository.findByEmailIgnoreCase("bob@example.com")).thenReturn(Optional.of(unverifiedUser));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("verify your email");

        verify(jwtService, never()).generateAccessToken(any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Login enforces rate limiting after 5 attempts")
    void login_rateLimitExceeded_throwsRateLimitExceededException() {
        LoginRequest request = new LoginRequest("alice@example.com", "Password@123");

        // First 5 attempts should pass rate limiting but fail authentication
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(BadCredentialsException.class);
        }

        // 6th attempt should hit rate limit
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("Too many login attempts");
    }

    @Test
    @DisplayName("Login normalizes email to lowercase")
    void login_emailNormalization_convertsToLowercase() {
        LoginRequest request = new LoginRequest("ALICE@EXAMPLE.COM", "Password@123");

        Authentication auth = mock(Authentication.class);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(auth.getPrincipal()).thenReturn(userDetails);
        when(userRepository.findByEmailIgnoreCase("alice@example.com")).thenReturn(Optional.of(verifiedUser));
        when(jwtService.generateAccessToken(userDetails)).thenReturn("jwtAccessToken123");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.login(request);

        verify(authenticationManager).authenticate(
                argThat(token -> ((UsernamePasswordAuthenticationToken) token).getPrincipal().equals("alice@example.com"))
        );
    }

    // ==================== Refresh Tests ====================

    @Test
    @DisplayName("Refresh returns new tokens and revokes old refresh token")
    void refresh_validToken_returnsNewTokens() {
        String rawRefreshToken = "validRawRefreshToken1234567890abcdef123456";
        String refreshTokenHash = tokenService.hashToken(rawRefreshToken);

        RefreshToken storedToken = RefreshToken.builder()
                .id(1L)
                .user(verifiedUser)
                .tokenHash(refreshTokenHash)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByTokenHash(refreshTokenHash)).thenReturn(Optional.of(storedToken));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userDetailsService.loadUserByUsername("alice@example.com")).thenReturn(userDetails);
        when(jwtService.generateAccessToken(userDetails)).thenReturn("newJwtAccessToken");

        RefreshResponse response = authService.refresh(rawRefreshToken);

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("newJwtAccessToken");
        assertThat(response.refreshToken()).isNotNull().isNotEmpty();
        assertThat(response.accessTokenExpiresInMs()).isEqualTo(900000L);

        // Old token should be revoked
        assertThat(storedToken.isRevoked()).isTrue();

        // New refresh token should be saved
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Refresh fails for unknown refresh token")
    void refresh_unknownToken_throwsInvalidTokenException() {
        String rawRefreshToken = "unknownRefreshToken";
        String refreshTokenHash = tokenService.hashToken(rawRefreshToken);

        when(refreshTokenRepository.findByTokenHash(refreshTokenHash)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(rawRefreshToken))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("Invalid refresh token");
    }

    @Test
    @DisplayName("Refresh fails for revoked token (reuse detection)")
    void refresh_revokedToken_throwsInvalidTokenException() {
        String rawRefreshToken = "revokedRefreshToken1234567890abcdef123456";
        String refreshTokenHash = tokenService.hashToken(rawRefreshToken);

        RefreshToken storedToken = RefreshToken.builder()
                .id(1L)
                .user(verifiedUser)
                .tokenHash(refreshTokenHash)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .revoked(true)
                .build();

        when(refreshTokenRepository.findByTokenHash(refreshTokenHash)).thenReturn(Optional.of(storedToken));

        assertThatThrownBy(() -> authService.refresh(rawRefreshToken))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("revoked");
    }

    @Test
    @DisplayName("Refresh fails for expired token")
    void refresh_expiredToken_throwsInvalidTokenException() {
        String rawRefreshToken = "expiredRefreshToken1234567890abcdef123456";
        String refreshTokenHash = tokenService.hashToken(rawRefreshToken);

        RefreshToken storedToken = RefreshToken.builder()
                .id(1L)
                .user(verifiedUser)
                .tokenHash(refreshTokenHash)
                .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByTokenHash(refreshTokenHash)).thenReturn(Optional.of(storedToken));

        assertThatThrownBy(() -> authService.refresh(rawRefreshToken))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("Refresh fails for blank token")
    void refresh_blankToken_throwsInvalidTokenException() {
        assertThatThrownBy(() -> authService.refresh(""))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("blank");

        assertThatThrownBy(() -> authService.refresh(null))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("blank");
    }

    // ==================== Logout Tests ====================

    @Test
    @DisplayName("Logout revokes the refresh token")
    void logout_validToken_revokesToken() {
        String rawRefreshToken = "logoutRefreshToken1234567890abcdef123456";
        String refreshTokenHash = tokenService.hashToken(rawRefreshToken);

        RefreshToken storedToken = RefreshToken.builder()
                .id(1L)
                .user(verifiedUser)
                .tokenHash(refreshTokenHash)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByTokenHash(refreshTokenHash)).thenReturn(Optional.of(storedToken));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.logout(rawRefreshToken);

        assertThat(storedToken.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(storedToken);
    }

    @Test
    @DisplayName("Logout with unknown token does nothing")
    void logout_unknownToken_noOp() {
        String rawRefreshToken = "unknownToken";
        String refreshTokenHash = tokenService.hashToken(rawRefreshToken);

        when(refreshTokenRepository.findByTokenHash(refreshTokenHash)).thenReturn(Optional.empty());

        authService.logout(rawRefreshToken);

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Logout with blank token does nothing")
    void logout_blankToken_noOp() {
        authService.logout("");
        authService.logout(null);

        verify(refreshTokenRepository, never()).findByTokenHash(anyString());
    }

    @Test
    @DisplayName("Logout with already revoked token does not save again")
    void logout_alreadyRevokedToken_noSave() {
        String rawRefreshToken = "revokedToken1234567890abcdef123456789012";
        String refreshTokenHash = tokenService.hashToken(rawRefreshToken);

        RefreshToken storedToken = RefreshToken.builder()
                .id(1L)
                .user(verifiedUser)
                .tokenHash(refreshTokenHash)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .revoked(true)
                .build();

        when(refreshTokenRepository.findByTokenHash(refreshTokenHash)).thenReturn(Optional.of(storedToken));

        authService.logout(rawRefreshToken);

        verify(refreshTokenRepository, never()).save(any());
    }
}
