package com.smarttaskmanager.scheduling.dto;

import java.time.Instant;

/**
 * Represents a single scheduled task slot in a proposed schedule.
 */
public record ScheduledTaskSlot(
        Long taskId,
        String taskTitle,
        String priority,
        Instant scheduledStartAt,
        Instant scheduledEndAt,
        Integer estimatedDurationMinutes,
        String reason
) {}
