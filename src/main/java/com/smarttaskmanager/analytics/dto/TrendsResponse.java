package com.smarttaskmanager.analytics.dto;

import java.util.List;
import java.util.Map;

/**
 * Response DTO for completion trends.
 */
public record TrendsResponse(
        List<DailyCompletionResponse> dailyStats,
        double averageCompletionTimeMinutes,
        double completionRate,
        List<String> insights
) {}
