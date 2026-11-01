package com.smarttaskmanager.task.dto;

import com.smarttaskmanager.task.entity.TaskPriority;
import com.smarttaskmanager.task.entity.TaskStatus;

import java.time.Instant;

/**
 * Response DTO for a subtask.
 */
public record SubtaskResponse(
        Long id,
        Long parentTaskId,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        Instant dueDate,
        Instant completedAt,
        Integer position,
        Instant createdAt,
        Instant updatedAt
) {}
