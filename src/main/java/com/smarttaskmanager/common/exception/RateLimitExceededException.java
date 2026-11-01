package com.smarttaskmanager.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an IP or user exceeds rate-limiting thresholds on sensitive endpoints.
 */
public class RateLimitExceededException extends BaseApiException {

    public RateLimitExceededException(String message) {
        super(message, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED");
    }
}
