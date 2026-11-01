package com.smarttaskmanager.scheduling.dto;

import java.time.Instant;

/**
 * Represents a scheduling conflict between two tasks.
 */
public record SchedulingConflict(
        Long taskId1,
        String taskTitle1,
        Long taskId2,
        String taskTitle2,
        Instant overlapStart,
        Instant overlapEnd,
        String description
) {}
