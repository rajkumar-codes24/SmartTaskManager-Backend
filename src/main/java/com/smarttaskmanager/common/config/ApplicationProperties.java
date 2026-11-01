package com.smarttaskmanager.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly-typed application properties bound to the 'app' prefix in application.properties.
 */
@ConfigurationProperties(prefix = "app")
public record ApplicationProperties(
        Mail mail,
        Jwt jwt,
        Reminder reminder,
        String publicBaseUrl,
        String frontendBaseUrl
) {
    public record Mail(
            String from,
            String fromName
    ) {}

    public record Jwt(
            String secret,
            long accessTokenExpirationMs,
            long refreshTokenExpirationMs,
            long verificationTokenExpirationMs,
            long passwordResetTokenExpirationMs
    ) {}

    public record Reminder(
            long schedulerIntervalMs,
            int maxBatchSize,
            int maxRetries
    ) {}
}
