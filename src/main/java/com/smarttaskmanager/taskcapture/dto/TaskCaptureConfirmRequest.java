package com.smarttaskmanager.taskcapture.dto;

import com.smarttaskmanager.task.entity.RecurrenceType;
import com.smarttaskmanager.task.entity.TaskPriority;
import com.smarttaskmanager.task.entity.TaskStatus;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

/**
 * Request payload for confirming and creating a parsed task.
 * User can modify the parsed values before confirmation.
 */
public record TaskCaptureConfirmRequest(
        @NotBlank(message = "Task title is required")
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        Instant dueDate,
        String categoryName,
        Integer estimatedDurationMinutes,
        RecurrenceType recurrenceType,
        Integer recurrenceInterval
) {}
