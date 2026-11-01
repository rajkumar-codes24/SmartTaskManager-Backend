package com.smarttaskmanager.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Abstract base class for domain and API exceptions.
 *
 * <p>Enables individual exceptions to explicitly define their HTTP status
 * and an uppercase machine-readable error code (e.g. RESOURCE_NOT_FOUND).</p>
 */
@Getter
public abstract class BaseApiException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final String errorCode;

    protected BaseApiException(String message, HttpStatus httpStatus, String errorCode) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    protected BaseApiException(String message, Throwable cause, HttpStatus httpStatus, String errorCode) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }
}
