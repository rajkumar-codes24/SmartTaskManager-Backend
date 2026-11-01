package com.smarttaskmanager.auth.controller;

import com.smarttaskmanager.auth.dto.LoginRequest;
import com.smarttaskmanager.auth.dto.LoginResponse;
import com.smarttaskmanager.auth.dto.PasswordResetRequest;
import com.smarttaskmanager.auth.dto.RefreshRequest;
import com.smarttaskmanager.auth.dto.RefreshResponse;
import com.smarttaskmanager.auth.dto.RegisterRequest;
import com.smarttaskmanager.auth.dto.ResendVerificationRequest;
import com.smarttaskmanager.auth.dto.ResetPasswordConfirmation;
import com.smarttaskmanager.auth.dto.VerifyEmailRequest;
import com.smarttaskmanager.auth.service.AuthService;
import com.smarttaskmanager.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing authentication lifecycle endpoints: registration, email verification, login, refresh, and logout.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Endpoints for user registration and email verification")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user account", description = "Creates an unverified account and sends an email verification link")
    public ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registration successful. Please check your email to verify your account."));
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Verify account email address via POST", description = "Consumes a verification token and activates the account")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request.token());
        return ResponseEntity.ok(ApiResponse.success("Email address verified successfully. You may now log in."));
    }

    @GetMapping("/verify-email")
    @Operation(summary = "Verify account email address via GET link", description = "Enables verification when clicking directly from an email client")
    public ResponseEntity<ApiResponse<Void>> verifyEmailByGet(@RequestParam("token") String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(ApiResponse.success("Email address verified successfully. You may now log in."));
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Resend verification email", description = "Sends a new verification link for an unverified account (rate-limited)")
    public ResponseEntity<ApiResponse<Void>> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request);
        return ResponseEntity.ok(ApiResponse.success("If an unverified account exists for this email, a verification link has been sent."));
    }

    @PostMapping("/login")
    @Operation(summary = "Login with email and password", description = "Authenticates a user and returns JWT access token and refresh token. Rate-limited to 5 attempts per 5 minutes.")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful.", response));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token", description = "Validates a refresh token, rotates it securely, and returns a new access token and refresh token.")
    public ResponseEntity<ApiResponse<RefreshResponse>> refresh(@Valid @RequestBody RefreshRequest request) {
        RefreshResponse response = authService.refresh(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully.", response));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout and revoke refresh token", description = "Revokes the given refresh token so it cannot be reused. Existing access tokens remain valid until expiration.")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully."));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request password reset", description = "Initiates a password reset by sending a reset link to the provided email. Returns a consistent response regardless of whether the email exists, preventing account enumeration. Rate-limited to 3 requests per 10 minutes.")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody PasswordResetRequest request) {
        authService.requestPasswordReset(request);
        return ResponseEntity.ok(ApiResponse.success("If an account exists for this email, a password reset link has been sent."));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Confirm password reset via POST", description = "Confirms a password reset using a valid reset token and sets a new password. Revokes all existing refresh tokens to invalidate previous sessions.")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordConfirmation request) {
        authService.confirmPasswordReset(request);
        return ResponseEntity.ok(ApiResponse.success("Password has been reset successfully. You may now log in with your new password."));
    }

    @GetMapping("/reset-password")
    @Operation(summary = "Confirm password reset via GET link", description = "Enables password reset when clicking directly from an email client. Redirects to a confirmation page after successful reset.")
    public ResponseEntity<ApiResponse<Void>> resetPasswordByGet(@RequestParam("token") String token) {
        // GET endpoint only validates the token exists and is not expired/consumed.
        // The actual password reset requires a POST with the new password.
        // This endpoint is used by email clients to verify the token is valid.
        // For full reset via GET link, the frontend would handle the password input form.
        // Here we delegate to the service to check token validity.
        authService.validateResetToken(token);
        return ResponseEntity.ok(ApiResponse.success("Password reset token is valid. Please proceed to set your new password."));
    }
}
