package com.smarttaskmanager.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for refreshing access tokens.
 */
public record RefreshRequest(
        @NotBlank(message = "Refresh token is required")
        String refreshToken
) {
}
