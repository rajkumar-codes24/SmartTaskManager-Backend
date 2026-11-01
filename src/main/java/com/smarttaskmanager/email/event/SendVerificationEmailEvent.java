package com.smarttaskmanager.email.event;

/**
 * Event published when an email verification link is generated for a user.
 *
 * <p>Dispatched to {@code @TransactionalEventListener(phase = AFTER_COMMIT)} so that
 * email transmission occurs only after the database transaction commits successfully.</p>
 */
public record SendVerificationEmailEvent(
        Long userId,
        String email,
        String fullName,
        String rawToken
) {}
