package com.smarttaskmanager.scheduling.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.Set;

/**
 * Request DTO for creating or updating user scheduling preferences.
 */
public record UpdateSchedulingPreferencesRequest(
        @NotBlank(message = "Timezone is required")
        String timezone,

        @NotBlank(message = "Work day start time is required")
        String workDayStartTime,

        @NotBlank(message = "Work day end time is required")
        String workDayEndTime,

        Set<String> workDaysOfWeek,

        @Min(value = 1, message = "Default task duration must be at least 1 minute")
        @Max(value = 480, message = "Default task duration must not exceed 480 minutes")
        Integer defaultTaskDurationMinutes,

        @Min(value = 0, message = "Break duration must be at least 0 minutes")
        @Max(value = 120, message = "Break duration must not exceed 120 minutes")
        Integer breakDurationMinutes
) {}
