package com.smarttaskmanager.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for initiating a password reset.
 *
 * <p>Returns a consistent response regardless of whether the email exists, preventing account enumeration.</p>
 */
public record PasswordResetRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        @Size(max = 150, message = "Email must not exceed 150 characters")
        String email
) {}
