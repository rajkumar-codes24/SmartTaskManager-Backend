package com.smarttaskmanager.reminder.service;

import com.smarttaskmanager.common.exception.BusinessRuleException;
import com.smarttaskmanager.common.exception.ResourceNotFoundException;
import com.smarttaskmanager.common.exception.UnauthorizedAccessException;
import com.smarttaskmanager.reminder.dto.CreateReminderRequest;
import com.smarttaskmanager.reminder.dto.ReminderResponse;
import com.smarttaskmanager.reminder.dto.UpdateReminderRequest;
import com.smarttaskmanager.reminder.entity.Reminder;
import com.smarttaskmanager.reminder.entity.ReminderStatus;
import com.smarttaskmanager.reminder.repository.ReminderRepository;
import com.smarttaskmanager.task.entity.Task;
import com.smarttaskmanager.task.repository.TaskRepository;
import com.smarttaskmanager.user.entity.User;
import com.smarttaskmanager.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Service for managing user-specific reminders.
 *
 * <p>All operations are scoped to the authenticated user. Users can only access,
 * modify, or delete their own reminders and can only create reminders for their own tasks.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderService {

    private final ReminderRepository reminderRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    /**
     * Creates a new reminder for the authenticated user.
     *
     * @param request the reminder creation request with taskId and scheduledAt
     * @return the created reminder as a response DTO
     * @throws ResourceNotFoundException if the task does not exist
     * @throws UnauthorizedAccessException if the task does not belong to the authenticated user
     * @throws BusinessRuleException if the scheduled date is in the past
     */
    @Transactional
    public ReminderResponse createReminder(CreateReminderRequest request) {
        User user = authenticatedUser();

        // Validate task ownership
        Task task = taskRepository.findByIdAndUserId(request.taskId(), user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Task", "taskId", request.taskId()));

        // Validate scheduled date is not in the past
        if (request.scheduledAt().isBefore(Instant.now())) {
            throw new BusinessRuleException("Reminder scheduled date must be in the future.");
        }

        Reminder reminder = Reminder.builder()
                .user(user)
                .task(task)
                .scheduledAt(request.scheduledAt())
                .status(ReminderStatus.PENDING)
                .retryCount(0)
                .build();

        reminder = reminderRepository.save(reminder);
        log.info("Reminder created: ID {} for task ID {} by user ID {}", reminder.getId(), task.getId(), user.getId());
        return toResponse(reminder);
    }

    /**
     * Retrieves all reminders for the authenticated user.
     *
     * @return list of reminders belonging to the authenticated user
     */
    @Transactional(readOnly = true)
    public List<ReminderResponse> getUserReminders() {
        User user = authenticatedUser();
        List<Reminder> reminders = reminderRepository.findByUserIdWithTask(user.getId());
        return reminders.stream().map(this::toResponse).toList();
    }

    /**
     * Retrieves a single reminder by ID, ensuring it belongs to the authenticated user.
     *
     * @param id the reminder ID
     * @return the reminder as a response DTO
     * @throws ResourceNotFoundException if the reminder does not exist
     * @throws UnauthorizedAccessException if the reminder does not belong to the authenticated user
     */
    @Transactional(readOnly = true)
    public ReminderResponse getReminderById(Long id) {
        User user = authenticatedUser();

        Reminder reminder = reminderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reminder", "id", id));

        if (!reminder.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedAccessException("You do not have access to this reminder.");
        }

        return toResponse(reminder);
    }

    /**
     * Updates an existing reminder belonging to the authenticated user.
     *
     * @param id the reminder ID
     * @param request the update request with optional fields
     * @return the updated reminder as a response DTO
     * @throws ResourceNotFoundException if the reminder does not exist
     * @throws UnauthorizedAccessException if the reminder does not belong to the authenticated user
     * @throws BusinessRuleException if updating a SENT or CANCELLED reminder
     */
    @Transactional
    public ReminderResponse updateReminder(Long id, UpdateReminderRequest request) {
        User user = authenticatedUser();

        Reminder reminder = reminderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reminder", "id", id));

        if (!reminder.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedAccessException("You do not have access to this reminder.");
        }

        // Prevent updating already sent or cancelled reminders
        if (reminder.getStatus() == ReminderStatus.SENT || reminder.getStatus() == ReminderStatus.CANCELLED) {
            throw new BusinessRuleException(
                    "Cannot update a reminder with status " + reminder.getStatus() + ". Only PENDING or FAILED reminders can be updated.");
        }

        if (request.scheduledAt() != null) {
            reminder.setScheduledAt(request.scheduledAt());
        }

        if (request.status() != null) {
            reminder.setStatus(request.status());
        }

        reminder = reminderRepository.save(reminder);
        log.info("Reminder updated: ID {} by user ID {}", reminder.getId(), user.getId());
        return toResponse(reminder);
    }

    /**
     * Deletes a reminder belonging to the authenticated user.
     *
     * @param id the reminder ID
     * @throws ResourceNotFoundException if the reminder does not exist
     * @throws UnauthorizedAccessException if the reminder does not belong to the authenticated user
     */
    @Transactional
    public void deleteReminder(Long id) {
        User user = authenticatedUser();

        Reminder reminder = reminderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reminder", "id", id));

        if (!reminder.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedAccessException("You do not have access to this reminder.");
        }

        reminderRepository.delete(reminder);
        log.info("Reminder deleted: ID {} by user ID {}", id, user.getId());
    }

    /**
     * Finds due PENDING reminders for the scheduler to process.
     * Used internally by the scheduler; not exposed via REST.
     *
     * @param threshold the time threshold (usually now)
     * @param maxBatchSize the maximum number of reminders to fetch
     * @return list of due reminders ready for processing
     */
    @Transactional
    public List<Reminder> findDueRemindersForProcessing(Instant threshold, int maxBatchSize) {
        List<Reminder> dueReminders = reminderRepository.findDueRemindersWithDetails(ReminderStatus.PENDING, threshold);
        // Limit batch size to avoid loading all reminders into memory
        if (dueReminders.size() > maxBatchSize) {
            return dueReminders.subList(0, maxBatchSize);
        }
        return dueReminders;
    }

    /**
     * Marks a reminder as SENT after successful email delivery.
     *
     * @param reminder the reminder to mark as sent
     */
    @Transactional
    public void markReminderAsSent(Reminder reminder) {
        reminder.setStatus(ReminderStatus.SENT);
        reminder.setSentAt(Instant.now());
        reminderRepository.save(reminder);
    }

    /**
     * Marks a reminder as SENT after successful email delivery, using only the ID.
     * Used by the scheduler which works with extracted data, not entity instances.
     *
     * @param reminderId the reminder ID to mark as sent
     */
    @Transactional
    public void markReminderAsSentById(Long reminderId) {
        Reminder reminder = reminderRepository.findById(reminderId)
                .orElseThrow(() -> new ResourceNotFoundException("Reminder", "id", reminderId));
        reminder.setStatus(ReminderStatus.SENT);
        reminder.setSentAt(Instant.now());
        reminderRepository.save(reminder);
    }

    /**
     * Marks a reminder as FAILED after email delivery failure.
     *
     * @param reminder the reminder to mark as failed
     * @param reason the failure reason
     */
    @Transactional
    public void markReminderAsFailed(Reminder reminder, String reason) {
        reminder.setRetryCount(reminder.getRetryCount() + 1);
        reminder.setFailureReason(reason);
        if (reminder.getRetryCount() >= 3) {
            reminder.setStatus(ReminderStatus.FAILED);
        }
        reminderRepository.save(reminder);
    }

    /**
     * Marks a reminder as FAILED after email delivery failure, using only the ID.
     * Used by the scheduler which works with extracted data, not entity instances.
     *
     * @param reminderId the reminder ID to mark as failed
     * @param reason the failure reason
     */
    @Transactional
    public void markReminderAsFailedById(Long reminderId, String reason) {
        Reminder reminder = reminderRepository.findById(reminderId)
                .orElseThrow(() -> new ResourceNotFoundException("Reminder", "id", reminderId));
        reminder.setRetryCount(reminder.getRetryCount() + 1);
        reminder.setFailureReason(reason);
        if (reminder.getRetryCount() >= 3) {
            reminder.setStatus(ReminderStatus.FAILED);
        }
        reminderRepository.save(reminder);
    }

    /**
     * Converts a Reminder entity to a ReminderResponse DTO.
     */
    private ReminderResponse toResponse(Reminder reminder) {
        return new ReminderResponse(
                reminder.getId(),
                reminder.getTask().getId(),
                reminder.getTask().getTitle(),
                reminder.getScheduledAt(),
                reminder.getStatus(),
                reminder.getSentAt(),
                reminder.getRetryCount(),
                reminder.getCreatedAt()
        );
    }

    /**
     * Derives the authenticated user from the security context.
     * Never trusts user ID from request parameters.
     */
    private User authenticatedUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UnauthorizedAccessException("Authenticated user not found in database."));
    }
}
