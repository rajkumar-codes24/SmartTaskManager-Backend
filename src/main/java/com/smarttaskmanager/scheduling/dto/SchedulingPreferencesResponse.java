package com.smarttaskmanager.scheduling.dto;

import java.time.LocalTime;
import java.util.Set;

/**
 * Response DTO for user scheduling preferences.
 */
public record SchedulingPreferencesResponse(
        Long id,
        String timezone,
        String workDayStartTime,
        String workDayEndTime,
        Set<String> workDaysOfWeek,
        Integer defaultTaskDurationMinutes,
        Integer breakDurationMinutes,
        long workingMinutesPerDay
) {}
