package com.smarttaskmanager.analytics.dto;

/**
 * Represents a single productivity insight.
 */
public record InsightItem(
        String type,
        String message,
        int severity
) {}
