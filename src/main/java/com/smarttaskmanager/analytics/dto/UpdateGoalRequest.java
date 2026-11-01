package com.smarttaskmanager.analytics.dto;

import jakarta.validation.constraints.Min;

import java.time.LocalDate;

/**
 * Request DTO for updating a productivity goal.
 */
public record UpdateGoalRequest(
        String goalType,

        @Min(value = 1, message = "Target count must be at least 1")
        Integer targetCount,

        LocalDate periodStartDate,

        LocalDate periodEndDate,

        Boolean isActive
) {}
