package com.smarttaskmanager.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for creating a label.
 */
public record CreateLabelRequest(
        @NotBlank(message = "Label name is required")
        @Size(max = 50, message = "Label name must not exceed 50 characters")
        String name,

        @Size(max = 7, message = "Color must be a valid hex code (e.g., #FF0000)")
        String color
) {}
