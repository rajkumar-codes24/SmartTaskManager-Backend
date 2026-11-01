package com.smarttaskmanager.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for requesting a new verification email.
 */
public record ResendVerificationRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        String email
) {}
