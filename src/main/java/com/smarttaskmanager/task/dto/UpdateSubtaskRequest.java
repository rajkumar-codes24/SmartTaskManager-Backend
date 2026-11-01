package com.smarttaskmanager.task.dto;

import com.smarttaskmanager.task.entity.TaskPriority;
import com.smarttaskmanager.task.entity.TaskStatus;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Request payload for updating a subtask. All fields are optional.
 */
public record UpdateSubtaskRequest(
        @Size(max = 150, message = "Subtask title must not exceed 150 characters")
        String title,

        @Size(max = 2000, message = "Description must not exceed 2000 characters")
        String description,

        TaskStatus status,

        TaskPriority priority,

        Instant dueDate
) {}
