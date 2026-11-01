package com.smarttaskmanager.auth.service;

import com.smarttaskmanager.auth.dto.LoginRequest;
import com.smarttaskmanager.auth.dto.LoginResponse;
import com.smarttaskmanager.auth.dto.PasswordResetRequest;
import com.smarttaskmanager.auth.dto.RefreshResponse;
import com.smarttaskmanager.auth.dto.RegisterRequest;
import com.smarttaskmanager.auth.dto.ResendVerificationRequest;
import com.smarttaskmanager.auth.dto.ResetPasswordConfirmation;
import com.smarttaskmanager.auth.entity.EmailVerificationToken;
import com.smarttaskmanager.auth.entity.PasswordResetToken;
import com.smarttaskmanager.auth.entity.RefreshToken;
import com.smarttaskmanager.auth.repository.EmailVerificationTokenRepository;
import com.smarttaskmanager.auth.repository.PasswordResetTokenRepository;
import com.smarttaskmanager.auth.repository.RefreshTokenRepository;
import com.smarttaskmanager.common.config.ApplicationProperties;
import com.smarttaskmanager.common.exception.BusinessRuleException;
import com.smarttaskmanager.common.exception.DuplicateResourceException;
import com.smarttaskmanager.common.exception.InvalidTokenException;
import com.smarttaskmanager.common.exception.RateLimitExceededException;
import com.smarttaskmanager.common.ratelimit.InMemoryRateLimiter;
import com.smarttaskmanager.email.event.SendPasswordResetEmailEvent;
import com.smarttaskmanager.email.event.SendVerificationEmailEvent;
import com.smarttaskmanager.security.jwt.JwtService;
import com.smarttaskmanager.security.user.UserDetailsServiceImpl;
import com.smarttaskmanager.user.entity.Role;
import com.smarttaskmanager.user.entity.User;
import com.smarttaskmanager.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Service orchestrating user registration, email verification, login, refresh token rotation, and logout.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;
    private final AuthenticationManager authenticationManager;
    private final ApplicationProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final InMemoryRateLimiter rateLimiter;

    /**
     * Registers a new user, hashes their password, persists an initial unverified account,
     * creates a hashed verification token, and dispatches a verification email event.
     */
    @Transactional
    public void register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            log.warn("Registration rejected: email {} already registered", normalizedEmail);
            throw new DuplicateResourceException("User", "email", normalizedEmail);
        }

        User user = User.builder()
                .fullName(request.fullName().trim())
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(Role.ROLE_USER)
                .enabled(true)
                .emailVerified(false)
                .build();

        user = userRepository.save(user);

        // Generate raw token for email and SHA-256 hash for database
        String rawToken = tokenService.generateRawToken();
        String tokenHash = tokenService.hashToken(rawToken);

        long expirationMs = properties.jwt().verificationTokenExpirationMs();
        Instant expiresAt = Instant.now().plusMillis(expirationMs);

        EmailVerificationToken verificationToken = EmailVerificationToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(expiresAt)
                .build();

        emailVerificationTokenRepository.save(verificationToken);

        // Publish event to dispatch email strictly AFTER transaction commits
        eventPublisher.publishEvent(new SendVerificationEmailEvent(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                rawToken
        ));

        log.info("Registered user [{}] with unverified email", normalizedEmail);
    }

    /**
     * Verifies an account using a single-use verification token.
     */
    @Transactional
    public void verifyEmail(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidTokenException("Verification token must not be blank");
        }

        String tokenHash = tokenService.hashToken(rawToken);

        EmailVerificationToken token = emailVerificationTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidTokenException("Invalid verification token"));

        if (token.isConsumed()) {
            log.warn("Verification rejected: token already consumed for user ID {}", token.getUser().getId());
            throw new InvalidTokenException("Verification token has already been used");
        }

        if (token.isExpired()) {
            log.warn("Verification rejected: token expired for user ID {}", token.getUser().getId());
            throw new InvalidTokenException("Verification token has expired", "TOKEN_EXPIRED");
        }

        // Consume the token (single-use) and mark user account verified
        token.setConsumedAt(Instant.now());
        emailVerificationTokenRepository.save(token);

        User user = token.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);

        log.info("User [{}] successfully verified email address", user.getEmail());
    }

    /**
     * Resends a verification email for an unverified account, protected by rate limiting.
     * Prevents account enumeration by returning normally even if the email does not exist.
     */
    @Transactional
    public void resendVerification(ResendVerificationRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        // Enforce rate limiting: maximum 1 request per 2 minutes per email
        String rateLimitKey = "resend_verif:" + normalizedEmail;
        if (!rateLimiter.tryAcquire(rateLimitKey, 1, Duration.ofMinutes(2))) {
            log.warn("Rate limit exceeded for resend verification on {}", normalizedEmail);
            throw new RateLimitExceededException("Verification email was sent recently. Please wait before requesting another.");
        }

        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(normalizedEmail);
        if (userOpt.isEmpty()) {
            // Anti-enumeration: Return silently without disclosing whether account exists
            log.info("Resend verification requested for non-existent email {}", normalizedEmail);
            return;
        }

        User user = userOpt.get();
        if (user.isEmailVerified()) {
            log.info("Resend verification requested for already verified user {}", normalizedEmail);
            return;
        }

        // Delete any existing unconsumed tokens for this user to enforce single active token
        emailVerificationTokenRepository.deleteAllByUser(user);

        String rawToken = tokenService.generateRawToken();
        String tokenHash = tokenService.hashToken(rawToken);

        long expirationMs = properties.jwt().verificationTokenExpirationMs();
        EmailVerificationToken verificationToken = EmailVerificationToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plusMillis(expirationMs))
                .build();

        emailVerificationTokenRepository.save(verificationToken);

        eventPublisher.publishEvent(new SendVerificationEmailEvent(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                rawToken
        ));

        log.info("Dispatched new verification email for user [{}]", normalizedEmail);
    }

    /**
     * Authenticates a user by email and password, checks email verification status,
     * and returns JWT access token plus a cryptographically secure refresh token.
     *
     * <p>Uses rate limiting to protect against brute-force attacks and anti-enumeration
     * to avoid revealing whether an email address exists in the system.</p>
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        // Rate limiting: max 5 login attempts per 5 minutes per email
        String rateLimitKey = "login:" + normalizedEmail;
        if (!rateLimiter.tryAcquire(rateLimitKey, 5, Duration.ofMinutes(5))) {
            log.warn("Rate limit exceeded for login on {}", normalizedEmail);
            // Anti-enumeration: return generic message without revealing account status
            throw new RateLimitExceededException("Too many login attempts. Please try again later.");
        }

        // Authenticate credentials using Spring Security's AuthenticationManager
        // This will throw BadCredentialsException if email or password is wrong
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizedEmail, request.password())
        );

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();

        // Load the full User entity to check email verification
        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new BusinessRuleException("Authentication failed"));

        // Reject login if email is not verified
        if (!user.isEmailVerified()) {
            log.warn("Login rejected: email not verified for user [{}]", normalizedEmail);
            throw new BusinessRuleException("Please verify your email address before logging in.");
        }

        // Generate JWT access token
        String accessToken = jwtService.generateAccessToken(userDetails);

        // Generate cryptographically secure refresh token
        String rawRefreshToken = tokenService.generateRawToken();
        String refreshTokenHash = tokenService.hashToken(rawRefreshToken);

        // Store only the hash in the database
        long refreshTokenExpirationMs = properties.jwt().refreshTokenExpirationMs();
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(refreshTokenHash)
                .expiresAt(Instant.now().plusMillis(refreshTokenExpirationMs))
                .build();
        refreshTokenRepository.save(refreshToken);

        // Update last login timestamp
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        log.info("User [{}] logged in successfully", normalizedEmail);

        return new LoginResponse(
                accessToken,
                rawRefreshToken,
                properties.jwt().accessTokenExpirationMs()
        );
    }

    /**
     * Refreshes access tokens using a valid refresh token with secure rotation.
     *
     * <p>The old refresh token is revoked immediately and replaced with a new one,
     * preventing token reuse attacks. If the same refresh token is used concurrently,
     * only the first request succeeds.</p>
     */
    @Transactional
    public RefreshResponse refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidTokenException("Refresh token must not be blank");
        }

        String refreshTokenHash = tokenService.hashToken(rawRefreshToken);

        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(refreshTokenHash)
                .orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));

        // Check if the token has already been revoked (possible reuse attack)
        if (storedToken.isRevoked()) {
            log.warn("Refresh token reuse detected for user ID {}", storedToken.getUser().getId());
            throw new InvalidTokenException("Refresh token has been revoked", "TOKEN_REVOKED");
        }

        // Check if the token has expired
        if (storedToken.isExpired()) {
            log.warn("Refresh token expired for user ID {}", storedToken.getUser().getId());
            throw new InvalidTokenException("Refresh token has expired", "TOKEN_EXPIRED");
        }

        // Revoke the old token immediately to prevent reuse
        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        User user = storedToken.getUser();

        // Load user details for JWT generation
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());

        // Generate new JWT access token
        String newAccessToken = jwtService.generateAccessToken(userDetails);

        // Generate a new refresh token (rotation)
        String newRawRefreshToken = tokenService.generateRawToken();
        String newRefreshTokenHash = tokenService.hashToken(newRawRefreshToken);

        long refreshTokenExpirationMs = properties.jwt().refreshTokenExpirationMs();
        RefreshToken newRefreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(newRefreshTokenHash)
                .expiresAt(Instant.now().plusMillis(refreshTokenExpirationMs))
                .build();
        refreshTokenRepository.save(newRefreshToken);

        log.info("Refresh token rotated for user [{}]", user.getEmail());

        return new RefreshResponse(
                newAccessToken,
                newRawRefreshToken,
                properties.jwt().accessTokenExpirationMs()
        );
    }

    /**
     * Logs out a user by revoking the given refresh token.
     *
     * <p>After logout, the refresh token cannot be used to obtain new access tokens.
     * Existing access tokens remain valid until they naturally expire.</p>
     */
    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            log.info("Logout requested with blank refresh token");
            return;
        }

        String refreshTokenHash = tokenService.hashToken(rawRefreshToken);

        refreshTokenRepository.findByTokenHash(refreshTokenHash)
                .ifPresent(token -> {
                    if (!token.isRevoked()) {
                        token.setRevoked(true);
                        refreshTokenRepository.save(token);
                        log.info("Refresh token revoked for user ID {}", token.getUser().getId());
                    }
                });
    }

    /**
     * Initiates a password reset by generating a single-use reset token and sending a reset email.
     *
     * <p>Returns a consistent response regardless of whether the email exists, preventing account enumeration.
     * Rate-limited to prevent email flooding: max 3 requests per 10 minutes per email address.</p>
     */
    @Transactional
    public void requestPasswordReset(PasswordResetRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        // Rate limiting: max 3 reset requests per 10 minutes per email
        String rateLimitKey = "password_reset:" + normalizedEmail;
        if (!rateLimiter.tryAcquire(rateLimitKey, 3, Duration.ofMinutes(10))) {
            log.warn("Rate limit exceeded for password reset on {}", normalizedEmail);
            throw new RateLimitExceededException("Too many password reset requests. Please try again later.");
        }

        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(normalizedEmail);
        if (userOpt.isEmpty()) {
            // Anti-enumeration: Return silently without disclosing whether account exists
            log.info("Password reset requested for non-existent email {}", normalizedEmail);
            return;
        }

        User user = userOpt.get();

        // Delete any existing unconsumed reset tokens for this user
        passwordResetTokenRepository.deleteAllByUser(user);

        // Generate raw token for email and SHA-256 hash for database
        String rawToken = tokenService.generateRawToken();
        String tokenHash = tokenService.hashToken(rawToken);

        long expirationMs = properties.jwt().passwordResetTokenExpirationMs();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plusMillis(expirationMs))
                .build();

        passwordResetTokenRepository.save(resetToken);

        // Publish event to dispatch email strictly AFTER transaction commits
        eventPublisher.publishEvent(new SendPasswordResetEmailEvent(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                rawToken
        ));

        log.info("Password reset email dispatched for user [{}]", normalizedEmail);
    }

    /**
     * Confirms a password reset using a valid, unconsumed, and unexpired reset token.
     *
     * <p>Updates the user's password using BCrypt hashing, consumes the reset token,
     * and revokes all existing refresh tokens to invalidate previous sessions.</p>
     */
    @Transactional
    public void confirmPasswordReset(ResetPasswordConfirmation request) {
        if (request.token() == null || request.token().isBlank()) {
            throw new InvalidTokenException("Reset token must not be blank");
        }

        String tokenHash = tokenService.hashToken(request.token());

        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidTokenException("Invalid or expired password reset token"));

        // Check if the token has already been consumed (single-use)
        if (resetToken.isConsumed()) {
            log.warn("Password reset rejected: token already consumed for user ID {}", resetToken.getUser().getId());
            throw new InvalidTokenException("Password reset token has already been used");
        }

        // Check if the token has expired
        if (resetToken.isExpired()) {
            log.warn("Password reset rejected: token expired for user ID {}", resetToken.getUser().getId());
            throw new InvalidTokenException("Password reset token has expired", "TOKEN_EXPIRED");
        }

        User user = resetToken.getUser();

        // Update the user's password with BCrypt hash
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        // Consume the reset token (single-use)
        resetToken.setConsumedAt(Instant.now());
        passwordResetTokenRepository.save(resetToken);

        // Revoke all existing refresh tokens to invalidate previous sessions
        refreshTokenRepository.findByUser(user)
                .forEach(token -> {
                    if (!token.isRevoked()) {
                        token.setRevoked(true);
                        refreshTokenRepository.save(token);
                    }
                });

        log.info("Password successfully reset for user [{}]", user.getEmail());
    }

    /**
     * Validates a password reset token without consuming it.
     *
     * <p>Used by the GET endpoint to verify that a token from an email link is valid
     * before the user is asked to enter a new password. Does not consume the token.</p>
     */
    public void validateResetToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidTokenException("Reset token must not be blank");
        }

        String tokenHash = tokenService.hashToken(rawToken);

        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidTokenException("Invalid or expired password reset token"));

        if (resetToken.isConsumed()) {
            throw new InvalidTokenException("Password reset token has already been used");
        }

        if (resetToken.isExpired()) {
            throw new InvalidTokenException("Password reset token has expired", "TOKEN_EXPIRED");
        }
    }
}
