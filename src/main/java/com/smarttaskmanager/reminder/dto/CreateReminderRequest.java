package com.smarttaskmanager.reminder.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Request payload for creating a new reminder.
 */
public record CreateReminderRequest(
        @NotNull(message = "Task ID is required")
        Long taskId,

        @NotNull(message = "Scheduled date and time is required")
        Instant scheduledAt
) {}
