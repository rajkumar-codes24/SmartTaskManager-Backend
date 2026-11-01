package com.smarttaskmanager.analytics.dto;

import com.smarttaskmanager.analytics.entity.ProductivityGoal;

import java.time.LocalDate;

/**
 * Response DTO for productivity goals.
 */
public record GoalResponse(
        Long id,
        String goalType,
        Integer targetCount,
        LocalDate periodStartDate,
        LocalDate periodEndDate,
        Integer currentCount,
        Boolean isActive,
        double progressPercentage
) {
    public static GoalResponse fromEntity(ProductivityGoal goal) {
        double progress = goal.getTargetCount() > 0
                ? (double) goal.getCurrentCount() / goal.getTargetCount() * 100.0
                : 0.0;
        return new GoalResponse(
                goal.getId(),
                goal.getGoalType().name(),
                goal.getTargetCount(),
                goal.getPeriodStartDate(),
                goal.getPeriodEndDate(),
                goal.getCurrentCount(),
                goal.getIsActive(),
                Math.min(progress, 100.0)
        );
    }
}
