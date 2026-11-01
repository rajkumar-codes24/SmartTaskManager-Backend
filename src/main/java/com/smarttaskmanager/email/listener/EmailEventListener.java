package com.smarttaskmanager.email.listener;

import com.smarttaskmanager.email.event.SendPasswordResetEmailEvent;
import com.smarttaskmanager.email.event.SendVerificationEmailEvent;
import com.smarttaskmanager.email.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Event listener that dispatches email operations exclusively after database transactions commit.
 *
 * <p>Ensures that external SMTP communication never holds open database connection locks
 * and that rollback of database transactions guarantees zero email transmission.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailEventListener {

    private final EmailService emailService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSendVerificationEmail(SendVerificationEmailEvent event) {
        log.debug("Processing AFTER_COMMIT verification email for user ID {}", event.userId());
        emailService.sendVerificationEmail(event.email(), event.fullName(), event.rawToken());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSendPasswordResetEmail(SendPasswordResetEmailEvent event) {
        log.debug("Processing AFTER_COMMIT password reset email for user ID {}", event.userId());
        emailService.sendPasswordResetEmail(event.email(), event.fullName(), event.rawToken());
    }
}
