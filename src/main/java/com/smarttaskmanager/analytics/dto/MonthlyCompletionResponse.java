package com.smarttaskmanager.analytics.dto;

/**
 * Response DTO for monthly completion statistics.
 */
public record MonthlyCompletionResponse(
        String month,
        long completedCount
) {}
