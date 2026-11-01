package com.smarttaskmanager.task.dto;

import com.smarttaskmanager.task.entity.TaskPriority;
import com.smarttaskmanager.task.entity.TaskStatus;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for task dependency status.
 */
public record DependencyStatusResponse(
        Long taskId,
        List<Long> dependencyTaskIds,
        List<Long> completedDependencyIds,
        boolean allDependenciesCompleted,
        boolean isBlocked
) {}
