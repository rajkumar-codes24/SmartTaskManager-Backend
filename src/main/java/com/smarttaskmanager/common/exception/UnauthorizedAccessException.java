package com.smarttaskmanager.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an authenticated user attempts to access or modify resources they do not own.
 */
public class UnauthorizedAccessException extends BaseApiException {

    public UnauthorizedAccessException(String message) {
        super(message, HttpStatus.FORBIDDEN, "ACCESS_DENIED");
    }
}
