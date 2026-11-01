package com.smarttaskmanager.analytics.dto;

import java.util.List;

/**
 * Response DTO for productivity insights.
 */
public record InsightsResponse(
        List<InsightItem> insights
) {}
