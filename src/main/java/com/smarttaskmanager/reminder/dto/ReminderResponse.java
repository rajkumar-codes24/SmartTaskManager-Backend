package com.smarttaskmanager.reminder.dto;

import com.smarttaskmanager.reminder.entity.ReminderStatus;

import java.time.Instant;

/**
 * Response DTO for a reminder.
 */
public record ReminderResponse(
        Long id,
        Long taskId,
        String taskTitle,
        Instant scheduledAt,
        ReminderStatus status,
        Instant sentAt,
        int retryCount,
        Instant createdAt
) {}
