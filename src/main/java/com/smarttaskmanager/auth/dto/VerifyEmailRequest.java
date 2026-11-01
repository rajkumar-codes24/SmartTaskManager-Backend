package com.smarttaskmanager.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for verifying an account using a verification token.
 */
public record VerifyEmailRequest(
        @NotBlank(message = "Verification token is required")
        String token
) {}
