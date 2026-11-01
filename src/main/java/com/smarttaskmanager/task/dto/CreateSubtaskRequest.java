package com.smarttaskmanager.task.dto;

import com.smarttaskmanager.task.entity.TaskPriority;
import com.smarttaskmanager.task.entity.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Request payload for creating a subtask under a parent task.
 */
public record CreateSubtaskRequest(
        @NotBlank(message = "Subtask title is required")
        @Size(max = 150, message = "Subtask title must not exceed 150 characters")
        String title,

        @Size(max = 2000, message = "Description must not exceed 2000 characters")
        String description,

        TaskStatus status,

        TaskPriority priority,

        Instant dueDate
) {}
