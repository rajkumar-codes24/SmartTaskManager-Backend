package com.smarttaskmanager.task.dto;

import com.smarttaskmanager.task.entity.TaskPriority;
import com.smarttaskmanager.task.entity.TaskStatus;

import java.util.Map;

/**
 * Response DTO for task statistics.
 */
public record TaskStatisticsResponse(
        long totalTasks,
        long completedTasks,
        long pendingTasks,
        long overdueTasks,
        Map<TaskStatus, Long> byStatus,
        Map<TaskPriority, Long> byPriority
) {}
