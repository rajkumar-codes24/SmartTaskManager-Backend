package com.smarttaskmanager.task.service;

import com.smarttaskmanager.common.exception.BusinessRuleException;
import com.smarttaskmanager.common.exception.ResourceNotFoundException;
import com.smarttaskmanager.task.dto.CreateRecurrenceRequest;
import com.smarttaskmanager.task.dto.RecurrenceResponse;
import com.smarttaskmanager.task.entity.RecurrenceType;
import com.smarttaskmanager.task.entity.Task;
import com.smarttaskmanager.task.entity.TaskRecurrenceRule;
import com.smarttaskmanager.task.entity.TaskStatus;
import com.smarttaskmanager.task.repository.TaskRecurrenceRuleRepository;
import com.smarttaskmanager.task.repository.TaskRepository;
import com.smarttaskmanager.user.entity.User;
import com.smarttaskmanager.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.zone.ZoneRules;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

/**
 * Service for managing task recurrence rules with lazy occurrence generation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskRecurrenceService {

    private final TaskRecurrenceRuleRepository recurrenceRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    @Transactional
    public RecurrenceResponse createRecurrence(Long taskId, CreateRecurrenceRequest request) {
        User user = getCurrentUser();
        Task task = taskRepository.findByIdAndUserId(taskId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Task", "id", taskId));

        // Check if recurrence already exists
        if (recurrenceRepository.findByTaskIdAndUserId(taskId, user.getId()).isPresent()) {
            throw new BusinessRuleException("This task already has a recurrence rule");
        }

        int interval = request.interval() != null ? request.interval() : 1;
        if (interval < 1) {
            throw new BusinessRuleException("Interval must be at least 1");
        }

        Instant nextOccurrence = calculateNextOccurrence(task.getDueDate(), request.recurrenceType(), interval,
                request.weekDays(), request.monthDay(), task.getTimezone());

        TaskRecurrenceRule rule = TaskRecurrenceRule.builder()
                .task(task)
                .user(user)
                .recurrenceType(request.recurrenceType())
                .recurrenceInterval(interval)
                .weekDays(request.weekDays())
                .monthDay(request.monthDay())
                .monthWeek(request.monthWeek())
                .occurrenceCount(request.occurrenceCount())
                .endDate(request.endDate())
                .isPaused(false)
                .nextOccurrenceAt(nextOccurrence)
                .build();

        rule = recurrenceRepository.save(rule);
        log.info("Recurrence rule created for task [{}] by user [{}]", taskId, user.getEmail());
        return toResponse(rule);
    }

    @Transactional(readOnly = true)
    public RecurrenceResponse getRecurrence(Long taskId) {
        User user = getCurrentUser();
        TaskRecurrenceRule rule = recurrenceRepository.findByTaskIdAndUserId(taskId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("RecurrenceRule", "task_id", taskId));
        return toResponse(rule);
    }

    @Transactional
    public RecurrenceResponse updateRecurrence(Long taskId, CreateRecurrenceRequest request) {
        User user = getCurrentUser();
        TaskRecurrenceRule rule = recurrenceRepository.findByTaskIdAndUserId(taskId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("RecurrenceRule", "task_id", taskId));

        int interval = request.interval() != null ? request.interval() : rule.getRecurrenceInterval();
        if (interval < 1) {
            throw new BusinessRuleException("Interval must be at least 1");
        }

        rule.setRecurrenceType(request.recurrenceType());
        rule.setRecurrenceInterval(interval);
        rule.setWeekDays(request.weekDays());
        rule.setMonthDay(request.monthDay());
        rule.setMonthWeek(request.monthWeek());
        rule.setOccurrenceCount(request.occurrenceCount());
        rule.setEndDate(request.endDate());

        Instant nextOccurrence = calculateNextOccurrence(rule.getTask().getDueDate(), request.recurrenceType(),
                interval, request.weekDays(), request.monthDay(), rule.getTask().getTimezone());
        rule.setNextOccurrenceAt(nextOccurrence);

        rule = recurrenceRepository.save(rule);
        log.info("Recurrence rule updated for task [{}] by user [{}]", taskId, user.getEmail());
        return toResponse(rule);
    }

    @Transactional
    public void deleteRecurrence(Long taskId) {
        User user = getCurrentUser();
        TaskRecurrenceRule rule = recurrenceRepository.findByTaskIdAndUserId(taskId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("RecurrenceRule", "task_id", taskId));
        recurrenceRepository.delete(rule);
        log.info("Recurrence rule deleted for task [{}] by user [{}]", taskId, user.getEmail());
    }

    @Transactional
    public RecurrenceResponse pauseRecurrence(Long taskId) {
        User user = getCurrentUser();
        TaskRecurrenceRule rule = recurrenceRepository.findByTaskIdAndUserId(taskId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("RecurrenceRule", "task_id", taskId));
        rule.setIsPaused(true);
        rule = recurrenceRepository.save(rule);
        log.info("Recurrence rule paused for task [{}] by user [{}]", taskId, user.getEmail());
        return toResponse(rule);
    }

    @Transactional
    public RecurrenceResponse resumeRecurrence(Long taskId) {
        User user = getCurrentUser();
        TaskRecurrenceRule rule = recurrenceRepository.findByTaskIdAndUserId(taskId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("RecurrenceRule", "task_id", taskId));
        rule.setIsPaused(false);
        rule = recurrenceRepository.save(rule);
        log.info("Recurrence rule resumed for task [{}] by user [{}]", taskId, user.getEmail());
        return toResponse(rule);
    }

    /**
     * Generates the next occurrence for a completed recurring task.
     * This is called lazily when the current occurrence is completed.
     * Uses optimistic locking via @Version to prevent concurrent read-modify-write races.
     * On OptimisticLockingFailureException, retries up to 3 times with a short backoff.
     * Each retry runs in a fresh transaction via doGenerateNextOccurrence's @Transactional annotation.
     *
     * SEMANTICS: generatedOccurrences tracks only subsequently generated occurrences.
     * The initial task is occurrence 0. If occurrenceCount=3, three additional occurrences
     * will be generated after the original task. When generatedOccurrences reaches
     * occurrenceCount, the rule is marked completed (not deleted) to preserve history.
     */
    public void generateNextOccurrence(Long taskId) {
        // Manual retry loop — each attempt runs in its own transaction via doGenerateNextOccurrence
        int maxRetries = 3;
        int retryDelayMs = 50;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                doGenerateNextOccurrence(taskId);
                return;
            } catch (ObjectOptimisticLockingFailureException e) {
                if (attempt < maxRetries) {
                    log.warn("Optimistic locking conflict on recurrence task [{}], attempt {}/{}. Retrying...",
                            taskId, attempt, maxRetries);
                    try {
                        Thread.sleep(retryDelayMs * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new BusinessRuleException("Recurrence generation interrupted");
                    }
                } else {
                    log.error("Optimistic locking conflict on recurrence task [{}] after {} attempts. Another process generated the next occurrence.",
                            taskId, maxRetries);
                    throw new BusinessRuleException(
                            "Another process is already updating this recurring task. Please try again.");
                }
            }
        }
    }

    /**
     * Internal method that performs the actual occurrence generation within a single transaction.
     * Each call runs in its own transaction so retries start fresh.
     * May throw ObjectOptimisticLockingFailureException if concurrent modification is detected.
     */
    @Transactional
    private void doGenerateNextOccurrence(Long taskId) {
        User user = getCurrentUser();
        TaskRecurrenceRule rule = recurrenceRepository.findByTaskIdAndUserId(taskId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("RecurrenceRule", "task_id", taskId));

        if (rule.getIsCompleted()) {
            log.info("Recurrence rule for task [{}] is already completed. No further occurrences.", taskId);
            return;
        }

        if (rule.getIsPaused()) {
            throw new BusinessRuleException("Recurrence is paused");
        }

        String timezone = rule.getTask().getTimezone();

        // Check if max occurrences reached — mark as completed instead of deleting
        if (rule.getOccurrenceCount() != null) {
            int generatedCount = rule.getGeneratedOccurrences() != null ? rule.getGeneratedOccurrences() : 0;
            if (generatedCount >= rule.getOccurrenceCount()) {
                log.info("Max occurrences reached for task [{}]. Generated [{}] of [{}]. Marking rule completed.",
                        taskId, generatedCount, rule.getOccurrenceCount());
                rule.setIsCompleted(true);
                recurrenceRepository.save(rule);
                return;
            }
        }

        // Calculate next occurrence using user's timezone
        Instant nextOccurrence = calculateNextOccurrence(rule.getTask().getDueDate(), rule.getRecurrenceType(),
                rule.getRecurrenceInterval(), rule.getWeekDays(), rule.getMonthDay(), timezone);

        // Check if end date passed — mark as completed instead of deleting
        if (rule.getEndDate() != null && nextOccurrence != null && nextOccurrence.isAfter(rule.getEndDate())) {
            log.info("End date reached for task [{}]. Marking rule completed.", taskId);
            rule.setIsCompleted(true);
            recurrenceRepository.save(rule);
            return;
        }

        if (nextOccurrence == null) {
            throw new BusinessRuleException("Could not calculate next occurrence");
        }

        // Increment the generated occurrences count — optimistic locking via @Version ensures
        // that concurrent modifications are detected and one of them will retry
        int newCount = (rule.getGeneratedOccurrences() != null ? rule.getGeneratedOccurrences() : 0) + 1;
        rule.setGeneratedOccurrences(newCount);
        rule.setLastOccurrenceAt(Instant.now());
        rule.setNextOccurrenceAt(nextOccurrence);
        recurrenceRepository.save(rule);

        // Update the task's due date to the next occurrence
        Task task = rule.getTask();
        task.setDueDate(nextOccurrence);
        task.setStatus(TaskStatus.TODO);
        task.setCompletedAt(null);
        taskRepository.save(task);

        log.info("Next occurrence generated for task [{}] at [{}]. Generated [{}] of [{}].",
                taskId, nextOccurrence, newCount, rule.getOccurrenceCount());
    }

    /**
     * Returns the number of occurrences that have been generated so far.
     * Uses the tracked generatedOccurrences counter for accurate counting.
     */
    private int countOccurrences(TaskRecurrenceRule rule) {
        return rule.getGeneratedOccurrences() != null ? rule.getGeneratedOccurrences() : 0;
    }

    /**
     * Resolves a timezone string to a ZoneId, validating the identifier.
     * Falls back to UTC if the string is null, blank, or invalid.
     */
    private ZoneId resolveTimezone(String timezone) {
        if (timezone == null || timezone.trim().isEmpty()) {
            return ZoneOffset.UTC;
        }
        try {
            ZoneId zoneId = ZoneId.of(timezone.trim());
            // Verify it's a valid timezone with known rules
            ZoneRules rules = zoneId.getRules();
            if (rules == null) {
                log.warn("Invalid timezone [{}], falling back to UTC", timezone);
                return ZoneOffset.UTC;
            }
            return zoneId;
        } catch (Exception e) {
            log.warn("Invalid timezone identifier [{}], falling back to UTC: {}", timezone, e.getMessage());
            return ZoneOffset.UTC;
        }
    }

    private Instant calculateNextOccurrence(Instant baseDate, RecurrenceType type, int interval,
                                            String weekDays, Integer monthDay, String timezone) {
        if (baseDate == null) return Instant.now().plusSeconds(86400);

        ZoneId zoneId = resolveTimezone(timezone);
        LocalDate date = baseDate.atZone(zoneId).toLocalDate();

        switch (type) {
            case DAILY:
            case EVERY_N_DAYS:
                return date.plusDays(interval).atStartOfDay(zoneId).toInstant();
            case WEEKDAYS:
                LocalDate next = date.plusDays(1);
                while (next.getDayOfWeek().getValue() > 5) {
                    next = next.plusDays(1);
                }
                return next.atStartOfDay(zoneId).toInstant();
            case WEEKLY:
                return date.plusWeeks(interval).atStartOfDay(zoneId).toInstant();
            case MONTHLY:
                // Handle month-end dates: if Jan 31 + 1 month = Feb 28/29, use the last day of the target month
                LocalDate monthlyDate = date.plusMonths(interval);
                // If the original day was greater than the target month's max day, cap to the last day
                int originalDay = date.getDayOfMonth();
                int maxDay = monthlyDate.lengthOfMonth();
                if (originalDay > maxDay) {
                    monthlyDate = monthlyDate.withDayOfMonth(maxDay);
                }
                return monthlyDate.atStartOfDay(zoneId).toInstant();
            case YEARLY:
                // Handle leap year: Feb 29 + 1 year = Feb 28 in non-leap years
                LocalDate yearlyDate = date.plusYears(interval);
                if (date.getMonthValue() == 2 && date.getDayOfMonth() == 29) {
                    // If target year is not a leap year, use Feb 28
                    if (!yearlyDate.isLeapYear()) {
                        yearlyDate = yearlyDate.withDayOfMonth(28);
                    }
                }
                return yearlyDate.atStartOfDay(zoneId).toInstant();
            case CUSTOM_WEEKLY:
                if (weekDays != null && !weekDays.isEmpty()) {
                    String[] days = weekDays.split(",");
                    for (String day : days) {
                        DayOfWeek dow = DayOfWeek.valueOf(day.trim().toUpperCase());
                        LocalDate candidate = date.with(TemporalAdjusters.nextOrSame(dow));
                        if (candidate.isAfter(date)) {
                            return candidate.atStartOfDay(zoneId).toInstant();
                        }
                    }
                }
                return date.plusWeeks(interval).atStartOfDay(zoneId).toInstant();
            default:
                return date.plusDays(interval).atStartOfDay(zoneId).toInstant();
        }
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    private RecurrenceResponse toResponse(TaskRecurrenceRule rule) {
        return new RecurrenceResponse(
                rule.getId(),
                rule.getTask().getId(),
                rule.getRecurrenceType(),
                rule.getRecurrenceInterval(),
                rule.getWeekDays(),
                rule.getMonthDay(),
                rule.getMonthWeek(),
                rule.getOccurrenceCount(),
                rule.getGeneratedOccurrences(),
                rule.getEndDate(),
                rule.getIsPaused(),
                rule.getIsCompleted(),
                rule.getLastOccurrenceAt(),
                rule.getNextOccurrenceAt()
        );
    }
}
