package com.smarttaskmanager.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an entity violates uniqueness constraints (e.g. duplicate email, duplicate category name).
 */
public class DuplicateResourceException extends BaseApiException {

    public DuplicateResourceException(String message) {
        super(message, HttpStatus.CONFLICT, "DUPLICATE_RESOURCE");
    }

    public DuplicateResourceException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s already exists with %s: '%s'", resourceName, fieldName, fieldValue),
                HttpStatus.CONFLICT, "DUPLICATE_RESOURCE");
    }
}
