package com.smarttaskmanager.analytics.dto;

import com.smarttaskmanager.analytics.entity.ProductivityGoal;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Request DTO for creating a productivity goal.
 */
public record CreateGoalRequest(
        @NotBlank(message = "Goal type is required")
        String goalType,

        @NotNull(message = "Target count is required")
        @Min(value = 1, message = "Target count must be at least 1")
        Integer targetCount,

        @NotNull(message = "Period start date is required")
        LocalDate periodStartDate,

        @NotNull(message = "Period end date is required")
        LocalDate periodEndDate
) {}
