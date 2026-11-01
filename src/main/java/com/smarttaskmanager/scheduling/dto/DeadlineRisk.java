package com.smarttaskmanager.scheduling.dto;

import java.time.Instant;

/**
 * Represents a task at risk of missing its deadline.
 */
public record DeadlineRisk(
        Long taskId,
        String taskTitle,
        Instant deadline,
        Instant estimatedCompletion,
        String reason
) {}
