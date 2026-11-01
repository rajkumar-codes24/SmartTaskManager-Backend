package com.smarttaskmanager.auth.dto;

/**
 * Response body for successful token refresh, containing new JWT access token and rotated refresh token.
 */
public record RefreshResponse(
        String accessToken,
        String refreshToken,
        long accessTokenExpiresInMs
) {
}
