package com.smarttaskmanager.reminder.dto;

import com.smarttaskmanager.reminder.entity.ReminderStatus;

import java.time.Instant;

/**
 * Request payload for updating an existing reminder.
 * All fields are optional; only the provided fields will be updated.
 */
public record UpdateReminderRequest(
        Instant scheduledAt,

        ReminderStatus status
) {}
