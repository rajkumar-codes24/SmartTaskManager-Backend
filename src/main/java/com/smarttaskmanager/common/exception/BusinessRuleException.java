package com.smarttaskmanager.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an operation violates domain business rules
 * (e.g., attempting to complete an already completed/cancelled task, setting invalid due date).
 */
public class BusinessRuleException extends BaseApiException {

    public BusinessRuleException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "BUSINESS_RULE_VIOLATION");
    }

    public BusinessRuleException(String message, String errorCode) {
        super(message, HttpStatus.BAD_REQUEST, errorCode);
    }
}
