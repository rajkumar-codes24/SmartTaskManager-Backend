package com.smarttaskmanager.taskcapture.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for parsing natural language into a task preview.
 */
public record TaskCaptureRequest(
        @NotBlank(message = "Task text is required")
        @Size(max = 1000, message = "Task text must not exceed 1000 characters")
        String text
) {}
