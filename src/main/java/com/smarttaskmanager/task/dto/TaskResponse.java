package com.smarttaskmanager.task.dto;

import com.smarttaskmanager.task.entity.TaskPriority;
import com.smarttaskmanager.task.entity.TaskStatus;

import java.time.Instant;

/**
 * Response DTO for a task.
 */
public record TaskResponse(
        Long id,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        Instant dueDate,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt,
        CategoryInfo category
) {
    /**
     * Lightweight category info embedded in task responses.
     */
    public record CategoryInfo(
            Long id,
            String name
    ) {}
}
