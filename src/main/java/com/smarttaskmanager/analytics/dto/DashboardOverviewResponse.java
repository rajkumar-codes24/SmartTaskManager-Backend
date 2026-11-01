package com.smarttaskmanager.analytics.dto;

import com.smarttaskmanager.task.entity.TaskPriority;
import com.smarttaskmanager.task.entity.TaskStatus;

import java.util.Map;

/**
 * Response DTO for dashboard overview analytics.
 */
public record DashboardOverviewResponse(
        long totalTasks,
        long completedTasks,
        long pendingTasks,
        long inProgressTasks,
        long overdueTasks,
        double completionPercentage,
        long completedToday,
        long completedThisWeek,
        long completedThisMonth
) {}
