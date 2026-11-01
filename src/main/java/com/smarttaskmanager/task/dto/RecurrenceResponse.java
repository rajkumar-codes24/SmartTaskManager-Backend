package com.smarttaskmanager.task.dto;

import com.smarttaskmanager.task.entity.RecurrenceType;

import java.time.Instant;

/**
 * Response DTO for a recurrence rule.
 */
public record RecurrenceResponse(
        Long id,
        Long taskId,
        RecurrenceType recurrenceType,
        Integer interval,
        String weekDays,
        Integer monthDay,
        String monthWeek,
        Integer occurrenceCount,
        Integer generatedOccurrences,
        Instant endDate,
        boolean isPaused,
        boolean isCompleted,
        Instant lastOccurrenceAt,
        Instant nextOccurrenceAt
) {}
