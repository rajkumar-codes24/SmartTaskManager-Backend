package com.smarttaskmanager.task.dto;

import jakarta.validation.constraints.Size;

/**
 * Request payload for updating a label. All fields are optional.
 */
public record UpdateLabelRequest(
        @Size(max = 50, message = "Label name must not exceed 50 characters")
        String name,

        @Size(max = 7, message = "Color must be a valid hex code (e.g., #FF0000)")
        String color
) {}
