package com.smarttaskmanager.task.dto;

import com.smarttaskmanager.task.entity.RecurrenceType;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Request payload for creating a recurrence rule on a task.
 */
public record CreateRecurrenceRequest(
        @NotNull(message = "Recurrence type is required")
        RecurrenceType recurrenceType,

        Integer interval,

        String weekDays,

        Integer monthDay,

        String monthWeek,

        Integer occurrenceCount,

        Instant endDate
) {}
