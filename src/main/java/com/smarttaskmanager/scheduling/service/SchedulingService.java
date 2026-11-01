package com.smarttaskmanager.scheduling.service;

import com.smarttaskmanager.common.exception.BusinessRuleException;
import com.smarttaskmanager.common.exception.ResourceNotFoundException;
import com.smarttaskmanager.scheduling.dto.DeadlineRisk;
import com.smarttaskmanager.scheduling.dto.ScheduledTaskSlot;
import com.smarttaskmanager.scheduling.dto.SchedulingConflict;
import com.smarttaskmanager.scheduling.dto.SchedulingPlanResponse;
import com.smarttaskmanager.scheduling.dto.SchedulingPreferencesResponse;
import com.smarttaskmanager.scheduling.dto.UpdateSchedulingPreferencesRequest;
import com.smarttaskmanager.scheduling.engine.SchedulingEngine;
import com.smarttaskmanager.scheduling.entity.UserSchedulingPreferences;
import com.smarttaskmanager.scheduling.repository.UserSchedulingPreferencesRepository;
import com.smarttaskmanager.task.entity.SchedulingStatus;
import com.smarttaskmanager.task.entity.Task;
import com.smarttaskmanager.task.entity.TaskDependency;
import com.smarttaskmanager.task.repository.TaskDependencyRepository;
import com.smarttaskmanager.task.repository.TaskRepository;
import com.smarttaskmanager.user.entity.User;
import com.smarttaskmanager.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service for managing user scheduling preferences and generating schedules.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulingService {

    private final UserSchedulingPreferencesRepository preferencesRepository;
    private final TaskRepository taskRepository;
    private final TaskDependencyRepository dependencyRepository;
    private final SchedulingEngine schedulingEngine;
    private final UserRepository userRepository;

    /**
     * Gets or creates default scheduling preferences for the authenticated user.
     */
    @Transactional(readOnly = true)
    public SchedulingPreferencesResponse getPreferences() {
        User user = getCurrentUser();
        UserSchedulingPreferences prefs = preferencesRepository.findByUserId(user.getId())
                .orElseGet(() -> {
                    // Create default preferences if none exist
                    UserSchedulingPreferences defaults = UserSchedulingPreferences.builder()
                            .user(user)
                            .workDayStartTime(java.time.LocalTime.of(9, 0))
                            .workDayEndTime(java.time.LocalTime.of(17, 0))
                            .workDaysOfWeek("MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY")
                            .defaultTaskDurationMinutes(60)
                            .breakDurationMinutes(15)
                            .timezone("UTC")
                            .build();
                    return preferencesRepository.save(defaults);
                });
        return toResponse(prefs);
    }

    /**
     * Updates the user's scheduling preferences.
     */
    @Transactional
    public SchedulingPreferencesResponse updatePreferences(UpdateSchedulingPreferencesRequest request) {
        User user = getCurrentUser();
        UserSchedulingPreferences prefs = preferencesRepository.findByUserId(user.getId())
                .orElseGet(() -> UserSchedulingPreferences.builder().user(user).build());

        // Validate timezone
        validateTimezone(request.timezone());

        // Validate work day times
        LocalTime startTime = LocalTime.parse(request.workDayStartTime());
        LocalTime endTime = LocalTime.parse(request.workDayEndTime());
        if (!endTime.isAfter(startTime)) {
            throw new BusinessRuleException("Work day end time must be after start time");
        }

        // Validate work days
        if (request.workDaysOfWeek() != null && !request.workDaysOfWeek().isEmpty()) {
            Set<String> validDays = Set.of(
                    "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"
            );
            for (String day : request.workDaysOfWeek()) {
                if (!validDays.contains(day.toUpperCase())) {
                    throw new BusinessRuleException("Invalid work day: " + day);
                }
            }
        }

        // Apply updates
        prefs.setWorkDayStartTime(startTime);
        prefs.setWorkDayEndTime(endTime);
        if (request.workDaysOfWeek() != null && !request.workDaysOfWeek().isEmpty()) {
            prefs.setWorkDaysOfWeek(request.workDaysOfWeek().stream()
                    .map(String::toUpperCase)
                    .collect(java.util.stream.Collectors.joining(",")));
        }
        if (request.defaultTaskDurationMinutes() != null) {
            prefs.setDefaultTaskDurationMinutes(request.defaultTaskDurationMinutes());
        }
        if (request.breakDurationMinutes() != null) {
            prefs.setBreakDurationMinutes(request.breakDurationMinutes());
        }
        prefs.setTimezone(request.timezone().toUpperCase());

        if (prefs.getId() == null) {
            prefs = preferencesRepository.save(prefs);
        } else {
            preferencesRepository.flush();
        }

        log.info("Scheduling preferences updated for user [{}]", user.getEmail());
        return toResponse(prefs);
    }

    /**
     * Generates a proposed schedule without modifying the database.
     */
    @Transactional(readOnly = true)
    public SchedulingPlanResponse generatePlan() {
        User user = getCurrentUser();

        // Get preferences
        UserSchedulingPreferences prefs = preferencesRepository.findByUserId(user.getId())
                .orElseGet(() -> {
                    UserSchedulingPreferences defaults = UserSchedulingPreferences.builder()
                            .user(user)
                            .workDayStartTime(LocalTime.of(9, 0))
                            .workDayEndTime(LocalTime.of(17, 0))
                            .workDaysOfWeek("MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY")
                            .defaultTaskDurationMinutes(60)
                            .breakDurationMinutes(15)
                            .timezone("UTC")
                            .build();
                    return defaults;
                });

        // Get all user tasks
        List<Task> tasks = taskRepository.findByUserId(user.getId(), org.springframework.data.domain.PageRequest.of(0, 1000))
                .getContent();

        // Get all user dependencies
        List<TaskDependency> dependencies = dependencyRepository.findByUserId(user.getId());

        return schedulingEngine.generatePlan(tasks, dependencies, prefs);
    }

    /**
     * Gets the current proposed schedule (same as generatePlan).
     */
    @Transactional(readOnly = true)
    public SchedulingPlanResponse getCurrentPlan() {
        return generatePlan();
    }

    /**
     * Applies the proposed schedule to the database.
     * Updates scheduledStartAt, scheduledEndAt, and schedulingStatus for each task.
     */
    @Transactional
    public SchedulingPlanResponse applyPlan() {
        User user = getCurrentUser();

        // Generate the plan first
        SchedulingPlanResponse plan = generatePlan();

        // Apply each scheduled slot
        for (ScheduledTaskSlot slot : plan.scheduledTasks()) {
            Task task = taskRepository.findByIdAndUserId(slot.taskId(), user.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Task", "id", slot.taskId()));

            // Only update if the task belongs to the user
            task.setScheduledStartAt(slot.scheduledStartAt());
            task.setScheduledEndAt(slot.scheduledEndAt());
            task.setSchedulingStatus(SchedulingStatus.SCHEDULED);
            taskRepository.save(task);
        }

        // Mark unschedulable tasks
        for (Long taskId : plan.unschedulableTaskIds()) {
            Task task = taskRepository.findByIdAndUserId(taskId, user.getId()).orElse(null);
            if (task != null) {
                task.setSchedulingStatus(SchedulingStatus.SKIPPED);
                taskRepository.save(task);
            }
        }

        log.info("Schedule applied for user [{}]: {} tasks scheduled, {} unschedulable",
                user.getEmail(), plan.scheduledTasks().size(), plan.unschedulableTaskIds().size());

        return plan;
    }

    /**
     * Gets scheduling conflicts for the user's tasks.
     */
    @Transactional(readOnly = true)
    public List<SchedulingConflict> getConflicts() {
        User user = getCurrentUser();
        List<Task> tasks = taskRepository.findByUserId(user.getId(), org.springframework.data.domain.PageRequest.of(0, 1000))
                .getContent();
        return schedulingEngine.detectTaskConflicts(tasks);
    }

    /**
     * Gets tasks at risk of missing deadlines.
     */
    @Transactional(readOnly = true)
    public List<DeadlineRisk> getDeadlineRisks() {
        User user = getCurrentUser();
        List<Task> tasks = taskRepository.findByUserId(user.getId(), org.springframework.data.domain.PageRequest.of(0, 1000))
                .getContent();
        return schedulingEngine.detectDeadlineRisks(tasks);
    }

    private SchedulingPreferencesResponse toResponse(UserSchedulingPreferences prefs) {
        return new SchedulingPreferencesResponse(
                prefs.getId(),
                prefs.getTimezone(),
                prefs.getWorkDayStartTime().toString(),
                prefs.getWorkDayEndTime().toString(),
                prefs.getWorkDaysSet(),
                prefs.getDefaultTaskDurationMinutes(),
                prefs.getBreakDurationMinutes(),
                prefs.getWorkingMinutesPerDay()
        );
    }

    private void validateTimezone(String timezone) {
        if (timezone == null || timezone.trim().isEmpty()) {
            throw new BusinessRuleException("Timezone is required");
        }
        try {
            ZoneId.of(timezone.trim());
        } catch (Exception e) {
            throw new BusinessRuleException("Invalid timezone: " + timezone);
        }
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }
}
