package com.smarttaskmanager.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an authentication, verification, or password reset token is invalid, expired, or revoked.
 */
public class InvalidTokenException extends BaseApiException {

    public InvalidTokenException(String message) {
        super(message, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN");
    }

    public InvalidTokenException(String message, String errorCode) {
        super(message, HttpStatus.UNAUTHORIZED, errorCode);
    }
}
