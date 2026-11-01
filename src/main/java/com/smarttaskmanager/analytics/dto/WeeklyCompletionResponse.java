package com.smarttaskmanager.analytics.dto;

/**
 * Response DTO for weekly completion statistics.
 */
public record WeeklyCompletionResponse(
        String week,
        long completedCount
) {}
