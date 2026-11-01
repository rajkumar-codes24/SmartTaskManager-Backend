package com.smarttaskmanager.taskcapture.dto;

/**
 * Response DTO for the parse endpoint.
 * Returns the parsed task data as a preview for user confirmation.
 */
public record TaskCaptureParseResponse(
        String title,
        String description,
        String status,
        String priority,
        String dueDate,
        String categoryName,
        Integer estimatedDurationMinutes,
        String recurrenceType,
        Integer recurrenceInterval,
        String ambiguities,
        String provider
) {
    public static TaskCaptureParseResponse fromResult(ParsedTaskResult result, String provider) {
        return new TaskCaptureParseResponse(
                result.title(),
                result.description(),
                result.status() != null ? result.status().name() : null,
                result.priority() != null ? result.priority().name() : null,
                result.dueDate() != null ? result.dueDate().toString() : null,
                result.categoryName(),
                result.estimatedDurationMinutes(),
                result.recurrenceType() != null ? result.recurrenceType().name() : null,
                result.recurrenceInterval(),
                result.ambiguities(),
                provider
        );
    }
}
