package com.smarttaskmanager.analytics.dto;

import java.time.LocalDate;

/**
 * Response DTO for daily completion statistics.
 */
public record DailyCompletionResponse(
        LocalDate date,
        long completedCount
) {}
