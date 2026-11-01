package com.smarttaskmanager.task.dto;

import com.smarttaskmanager.task.entity.TaskPriority;
import com.smarttaskmanager.task.entity.TaskStatus;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Request payload for updating an existing task.
 * All fields are optional; only the provided fields will be updated.
 */
public record UpdateTaskRequest(
        @Size(max = 150, message = "Task title must not exceed 150 characters")
        String title,

        @Size(max = 2000, message = "Description must not exceed 2000 characters")
        String description,

        TaskStatus status,

        TaskPriority priority,

        Instant dueDate,

        Long categoryId
) {}
