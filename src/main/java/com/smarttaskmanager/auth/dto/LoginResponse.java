package com.smarttaskmanager.auth.dto;

/**
 * Response body for successful login, containing JWT access token and refresh token.
 */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        long accessTokenExpiresInMs
) {
}
