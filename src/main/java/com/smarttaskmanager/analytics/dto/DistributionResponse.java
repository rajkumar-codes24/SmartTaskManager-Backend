package com.smarttaskmanager.analytics.dto;

import com.smarttaskmanager.task.entity.TaskPriority;
import com.smarttaskmanager.task.entity.TaskStatus;

import java.util.Map;

/**
 * Response DTO for task distribution by priority and category.
 */
public record DistributionResponse(
        Map<TaskPriority, Long> byPriority,
        Map<String, Long> byCategory
) {}
