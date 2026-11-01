package com.smarttaskmanager.task.dto;

/**
 * Response DTO for a label.
 */
public record LabelResponse(
        Long id,
        String name,
        String color
) {}
