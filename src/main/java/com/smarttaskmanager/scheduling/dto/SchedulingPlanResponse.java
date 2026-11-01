package com.smarttaskmanager.scheduling.dto;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for a proposed schedule plan.
 */
public record SchedulingPlanResponse(
        List<ScheduledTaskSlot> scheduledTasks,
        List<SchedulingConflict> conflicts,
        List<DeadlineRisk> deadlineRisks,
        List<Long> unschedulableTaskIds,
        Instant planGeneratedAt,
        String summary
) {}
