package com.smarttaskmanager.reminder.scheduler;

import com.smarttaskmanager.common.config.ApplicationProperties;
import com.smarttaskmanager.email.service.EmailService;
import com.smarttaskmanager.reminder.entity.Reminder;
import com.smarttaskmanager.reminder.service.ReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Scheduled background processor for due reminders.
 *
 * <p>Runs at a configurable interval to find PENDING reminders whose scheduled time
 * has passed, sends reminder emails, and records the outcome. Uses status-based
 * state transitions to prevent duplicate email delivery:</p>
 *
 * <ul>
 *   <li>PENDING → SENT: after successful email delivery</li>
 *   <li>PENDING → PENDING (retry): on transient failure, retryCount incremented</li>
 *   <li>PENDING → FAILED: after maxRetries failures</li>
 * </ul>
 *
 * <p>Each reminder is processed independently; a failure in one reminder does not
 * prevent processing of others.</p>
 *
 * <p>The scheduler extracts all necessary data within a transaction, then sends emails
 * and updates status outside the transaction to avoid holding DB connections open during SMTP.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderScheduler {

    private final ReminderService reminderService;
    private final EmailService emailService;
    private final ApplicationProperties applicationProperties;

    private static final DateTimeFormatter REMINDER_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a z")
            .withZone(ZoneId.systemDefault());

    /**
     * Data extracted from a reminder within a transaction for email sending.
     */
    private record ReminderData(
            Long reminderId,
            Long taskId,
            Long userId,
            String recipientEmail,
            String recipientName,
            String taskTitle,
            String taskDescription,
            String reminderTime
    ) {}

    /**
     * Processes due reminders at a fixed rate.
     *
     * <p>Configured via {@code app.reminder.scheduler-interval-ms} (default: 30000ms / 30 seconds)
     * and {@code app.reminder.initial-delay-ms} (default: 5000ms / 5 seconds).</p>
     *
     * <p>This method extracts reminder data within a transaction (to access lazy-loaded entities),
     * then sends emails and updates status outside the transaction to avoid holding DB connections
     * open during SMTP operations.</p>
     */
    @Scheduled(fixedRateString = "${app.reminder.scheduler-interval-ms:30000}", initialDelayString = "${app.reminder.initial-delay-ms:5000}")
    public void processDueReminders() {
        int maxBatchSize = applicationProperties.reminder().maxBatchSize();
        Instant now = Instant.now();

        log.info("Reminder scheduler running. Fetching due PENDING reminders...");

        // Extract reminder data within a transaction to access lazy-loaded entities
        List<ReminderData> reminderDataList = extractReminderData(now, maxBatchSize);

        if (reminderDataList.isEmpty()) {
            log.info("No due reminders to process.");
            return;
        }

        log.info("Processing {} due reminder(s).", reminderDataList.size());

        for (ReminderData data : reminderDataList) {
            try {
                processSingleReminder(data);
            } catch (Exception e) {
                // Catch all exceptions to prevent one reminder from blocking others
                log.error("Unexpected error processing reminder ID {}: {}", data.reminderId(), e.getMessage());
                try {
                    reminderService.markReminderAsFailedById(data.reminderId(), "Unexpected error: " + e.getMessage());
                } catch (Exception saveEx) {
                    log.error("Failed to save failure state for reminder ID {}: {}", data.reminderId(), saveEx.getMessage());
                }
            }
        }

        log.info("Reminder scheduler finished processing {} reminder(s).", reminderDataList.size());
    }

    /**
     * Extracts reminder data within a transaction to access lazy-loaded entities.
     * This method is transactional so that the JPA session remains open while accessing
     * reminder.getTask() and reminder.getUser().
     */
    @Transactional
    protected List<ReminderData> extractReminderData(Instant now, int maxBatchSize) {
        List<Reminder> dueReminders = reminderService.findDueRemindersForProcessing(now, maxBatchSize);

        List<ReminderData> dataList = new ArrayList<>();
        for (Reminder reminder : dueReminders) {
            // Access lazy-loaded properties within the transaction
            String reminderTime = REMINDER_TIME_FORMATTER.format(reminder.getScheduledAt());
            dataList.add(new ReminderData(
                    reminder.getId(),
                    reminder.getTask().getId(),
                    reminder.getUser().getId(),
                    reminder.getUser().getEmail(),
                    reminder.getUser().getFullName(),
                    reminder.getTask().getTitle(),
                    reminder.getTask().getDescription(),
                    reminderTime
            ));
        }
        return dataList;
    }

    /**
     * Processes a single reminder using pre-extracted data.
     * Email sending and status updates occur outside the transaction.
     *
     * <p>Each reminder is handled independently. On success, the reminder is marked SENT.
     * On failure, the retry count is incremented and the reminder is either kept PENDING
     * (for retry) or marked FAILED after exceeding max retries.</p>
     */
    private void processSingleReminder(ReminderData data) {
        log.info("Processing reminder ID {} for task ID {} (user ID {})",
                data.reminderId(), data.taskId(), data.userId());

        try {
            // Send the reminder email
            emailService.sendReminderEmail(
                    data.recipientEmail(),
                    data.recipientName(),
                    data.taskTitle(),
                    data.taskDescription(),
                    data.reminderTime()
            );

            // Mark as SENT only after successful email delivery
            reminderService.markReminderAsSentById(data.reminderId());
            log.info("Reminder ID {} successfully sent to {}", data.reminderId(), data.recipientEmail());

        } catch (Exception e) {
            // Email delivery failed — record the failure
            String reason = e.getMessage() != null ? e.getMessage() : "Unknown email delivery error";
            log.warn("Failed to send reminder ID {} to {}: {}", data.reminderId(), data.recipientEmail(), reason);
            reminderService.markReminderAsFailedById(data.reminderId(), reason);
        }
    }
}
