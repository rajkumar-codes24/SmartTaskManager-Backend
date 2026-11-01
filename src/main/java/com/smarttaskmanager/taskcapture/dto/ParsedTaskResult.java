package com.smarttaskmanager.taskcapture.dto;

import com.smarttaskmanager.task.entity.RecurrenceType;
import com.smarttaskmanager.task.entity.TaskPriority;
import com.smarttaskmanager.task.entity.TaskStatus;

import java.time.Instant;

/**
 * Result of parsing natural language into structured task data.
 * This is a preview; the user must confirm before task creation.
 */
public record ParsedTaskResult(
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        Instant dueDate,
        String categoryName,
        Integer estimatedDurationMinutes,
        RecurrenceType recurrenceType,
        Integer recurrenceInterval,
        String ambiguities
) {}
