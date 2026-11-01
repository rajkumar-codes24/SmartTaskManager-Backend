package com.smarttaskmanager.scheduling.engine;

import com.smarttaskmanager.scheduling.dto.DeadlineRisk;
import com.smarttaskmanager.scheduling.dto.ScheduledTaskSlot;
import com.smarttaskmanager.scheduling.dto.SchedulingConflict;
import com.smarttaskmanager.scheduling.dto.SchedulingPlanResponse;
import com.smarttaskmanager.scheduling.entity.UserSchedulingPreferences;
import com.smarttaskmanager.task.entity.Task;
import com.smarttaskmanager.task.entity.TaskDependency;
import com.smarttaskmanager.task.entity.TaskPriority;
import com.smarttaskmanager.task.entity.TaskStatus;
import com.smarttaskmanager.task.repository.TaskDependencyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Core scheduling engine that generates task schedules based on priorities, deadlines,
 * dependencies, working hours, and estimated durations.
 * <p>
 * Algorithm:
 * 1. Retrieve eligible unscheduled/incomplete tasks
 * 2. Sort by priority weight × urgency factor
 * 3. Apply topological sort for dependency ordering
 * 4. Greedily schedule tasks within working hours, respecting dependencies
 * 5. Detect conflicts and deadline risks
 * 6. Return proposed plan without modifying the database
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchedulingEngine {

    private final TaskDependencyRepository dependencyRepository;

    /**
     * Priority weight mapping for sorting. Higher weight = scheduled earlier.
     */
    private static final Map<TaskPriority, Integer> PRIORITY_WEIGHT = Map.of(
            TaskPriority.URGENT, 100,
            TaskPriority.HIGH, 75,
            TaskPriority.MEDIUM, 50,
            TaskPriority.LOW, 25
    );

    /**
     * Generates a proposed schedule for the given tasks and preferences.
     * Does NOT modify the database — returns a plan for user review.
     */
    public SchedulingPlanResponse generatePlan(
            List<Task> tasks,
            List<TaskDependency> dependencies,
            UserSchedulingPreferences preferences
    ) {
        log.info("Generating schedule for {} tasks with {} dependencies", tasks.size(), dependencies.size());

        // Filter to eligible tasks: not COMPLETED or CANCELLED
        List<Task> eligibleTasks = tasks.stream()
                .filter(t -> t.getStatus() != TaskStatus.COMPLETED && t.getStatus() != TaskStatus.CANCELLED)
                .toList();

        if (eligibleTasks.isEmpty()) {
            return emptyPlan("No eligible tasks to schedule");
        }

        ZoneId zoneId = resolveTimezone(preferences.getTimezone());

        // Build dependency graph
        Map<Long, Set<Long>> dependencyGraph = buildDependencyGraph(dependencies);

        // Topological sort for dependency ordering
        List<Long> topoOrder = topologicalSort(eligibleTasks, dependencyGraph);

        // Score and sort tasks by priority × urgency
        List<Task> sortedTasks = scoreAndSort(eligibleTasks, topoOrder, dependencyGraph, Instant.now());

        // Greedy scheduling within working hours
        List<ScheduledTaskSlot> scheduledSlots = new ArrayList<>();
        List<Long> unschedulableTaskIds = new ArrayList<>();
        Map<Long, Instant> taskScheduledEndTimes = new HashMap<>();

        // Start from today
        LocalDate currentDate = LocalDate.now(zoneId);
        Instant currentCursor = startOfWorkDay(currentDate, preferences, zoneId);

        for (Task task : sortedTasks) {
            Integer duration = task.getEstimatedDurationMinutes() != null
                    ? task.getEstimatedDurationMinutes()
                    : preferences.getDefaultTaskDurationMinutes();

            // Check dependency completion times
            Instant earliestStart = currentCursor;
            Set<Long> taskDeps = dependencyGraph.getOrDefault(task.getId(), Set.of());
            for (Long depId : taskDeps) {
                Instant depEnd = taskScheduledEndTimes.get(depId);
                if (depEnd != null && depEnd.isAfter(earliestStart)) {
                    earliestStart = depEnd;
                }
            }

            // Try to schedule within next 30 work days
            Instant scheduledStart = null;
            Instant scheduledEnd = null;
            String reason = null;

            for (int dayOffset = 0; dayOffset < 30; dayOffset++) {
                LocalDate tryDate = currentDate.plusDays(dayOffset);
                if (!isWorkDay(tryDate, preferences)) {
                    continue;
                }

                Instant dayStart = startOfWorkDay(tryDate, preferences, zoneId);
                Instant dayEnd = endOfWorkDay(tryDate, preferences, zoneId);

                // Ensure start is at or after earliestStart
                Instant slotStart = earliestStart.isAfter(dayStart) ? earliestStart : dayStart;
                if (slotStart.isAfter(dayEnd)) {
                    continue;
                }

                Instant slotEnd = slotStart.plus(duration, ChronoUnit.MINUTES);

                // Check if the task fits within this work day
                if (slotEnd.isAfter(dayEnd)) {
                    // Task doesn't fit in this day; try next work day
                    continue;
                }

                // Check for conflicts with already scheduled slots
                boolean hasConflict = scheduledSlots.stream().anyMatch(slot ->
                        !slot.scheduledEndAt().isBefore(slotStart) && !slot.scheduledStartAt().isAfter(slotEnd)
                );

                if (!hasConflict) {
                    scheduledStart = slotStart;
                    scheduledEnd = slotEnd;
                    reason = buildSchedulingReason(task, dayOffset, duration);
                    break;
                }
            }

            if (scheduledStart != null) {
                ScheduledTaskSlot slot = new ScheduledTaskSlot(
                        task.getId(),
                        task.getTitle(),
                        task.getPriority().name(),
                        scheduledStart,
                        scheduledEnd,
                        duration,
                        reason
                );
                scheduledSlots.add(slot);
                taskScheduledEndTimes.put(task.getId(), scheduledEnd);
            } else {
                unschedulableTaskIds.add(task.getId());
            }
        }

        // Detect conflicts
        List<SchedulingConflict> conflicts = detectSlotConflicts(scheduledSlots);

        // Detect deadline risks
        List<DeadlineRisk> deadlineRisks = detectDeadlineRisks(scheduledSlots, eligibleTasks);

        String summary = String.format(
                "Scheduled %d of %d tasks. %d unschedulable. %d conflicts detected. %d deadline risks.",
                scheduledSlots.size(), eligibleTasks.size(),
                unschedulableTaskIds.size(), conflicts.size(), deadlineRisks.size()
        );

        log.info("Schedule generated: {}", summary);

        return new SchedulingPlanResponse(
                scheduledSlots,
                conflicts,
                deadlineRisks,
                unschedulableTaskIds,
                Instant.now(),
                summary
        );
    }

    /**
     * Detects overlapping scheduled tasks from Task entities.
     */
    public List<SchedulingConflict> detectTaskConflicts(List<Task> tasks) {
        List<ScheduledTaskSlot> slots = tasks.stream()
                .filter(t -> t.getScheduledStartAt() != null && t.getScheduledEndAt() != null)
                .map(t -> new ScheduledTaskSlot(
                        t.getId(), t.getTitle(), t.getPriority().name(),
                        t.getScheduledStartAt(), t.getScheduledEndAt(),
                        t.getEstimatedDurationMinutes(), "Existing schedule"
                ))
                .toList();
        return detectSlotConflicts(slots);
    }

    /**
     * Detects tasks at risk of missing their deadlines.
     */
    public List<DeadlineRisk> detectDeadlineRisks(List<Task> tasks) {
        Instant now = Instant.now();
        List<DeadlineRisk> risks = new ArrayList<>();

        for (Task task : tasks) {
            if (task.getStatus() == TaskStatus.COMPLETED || task.getStatus() == TaskStatus.CANCELLED) {
                continue;
            }

            Instant deadline = task.getDeadline() != null ? task.getDeadline() : task.getDueDate();
            if (deadline == null) {
                continue;
            }

            if (deadline.isBefore(now)) {
                risks.add(new DeadlineRisk(
                        task.getId(),
                        task.getTitle(),
                        deadline,
                        null,
                        "Task is already past its deadline"
                ));
                continue;
            }

            // Check if estimated completion would miss the deadline
            Integer duration = task.getEstimatedDurationMinutes() != null
                    ? task.getEstimatedDurationMinutes()
                    : 60;

            Instant estimatedCompletion;
            if (task.getScheduledEndAt() != null) {
                estimatedCompletion = task.getScheduledEndAt();
            } else {
                estimatedCompletion = now.plus(duration, ChronoUnit.MINUTES);
            }

            if (estimatedCompletion.isAfter(deadline)) {
                risks.add(new DeadlineRisk(
                        task.getId(),
                        task.getTitle(),
                        deadline,
                        estimatedCompletion,
                        "Estimated completion time exceeds deadline"
                ));
            }
        }

        return risks;
    }

    private List<SchedulingConflict> detectSlotConflicts(List<ScheduledTaskSlot> slots) {
        List<SchedulingConflict> conflicts = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            for (int j = i + 1; j < slots.size(); j++) {
                ScheduledTaskSlot s1 = slots.get(i);
                ScheduledTaskSlot s2 = slots.get(j);

                if (s1.scheduledEndAt().isAfter(s2.scheduledStartAt()) &&
                        s1.scheduledStartAt().isBefore(s2.scheduledEndAt())) {
                    Instant overlapStart = s1.scheduledStartAt().isAfter(s2.scheduledStartAt())
                            ? s1.scheduledStartAt() : s2.scheduledStartAt();
                    Instant overlapEnd = s1.scheduledEndAt().isBefore(s2.scheduledEndAt())
                            ? s1.scheduledEndAt() : s2.scheduledEndAt();

                    conflicts.add(new SchedulingConflict(
                            s1.taskId(), s1.taskTitle(),
                            s2.taskId(), s2.taskTitle(),
                            overlapStart, overlapEnd,
                            "Tasks overlap in scheduled time"
                    ));
                }
            }
        }
        return conflicts;
    }

    private List<DeadlineRisk> detectDeadlineRisks(
            List<ScheduledTaskSlot> slots, List<Task> allTasks
    ) {
        List<DeadlineRisk> risks = new ArrayList<>();
        Map<Long, ScheduledTaskSlot> slotMap = slots.stream()
                .collect(Collectors.toMap(ScheduledTaskSlot::taskId, s -> s));

        for (Task task : allTasks) {
            ScheduledTaskSlot slot = slotMap.get(task.getId());
            if (slot == null) {
                continue;
            }

            Instant deadline = task.getDeadline() != null ? task.getDeadline() : task.getDueDate();
            if (deadline == null) {
                continue;
            }

            if (slot.scheduledEndAt().isAfter(deadline)) {
                risks.add(new DeadlineRisk(
                        task.getId(),
                        task.getTitle(),
                        deadline,
                        slot.scheduledEndAt(),
                        "Scheduled end time exceeds deadline"
                ));
            }
        }

        return risks;
    }

    private List<Long> topologicalSort(List<Task> tasks, Map<Long, Set<Long>> graph) {
        Set<Long> allTaskIds = tasks.stream().map(Task::getId).collect(Collectors.toSet());
        Set<Long> visited = new HashSet<>();
        List<Long> result = new ArrayList<>();

        for (Long taskId : allTaskIds) {
            if (!visited.contains(taskId)) {
                topoSortDFS(taskId, graph, visited, result);
            }
        }
        return result;
    }

    private void topoSortDFS(Long node, Map<Long, Set<Long>> graph, Set<Long> visited, List<Long> result) {
        visited.add(node);
        for (Long neighbor : graph.getOrDefault(node, Set.of())) {
            if (!visited.contains(neighbor)) {
                topoSortDFS(neighbor, graph, visited, result);
            }
        }
        result.add(node);
    }

    private List<Task> scoreAndSort(
            List<Task> tasks, List<Long> topoOrder, Map<Long, Set<Long>> graph, Instant now
    ) {
        // Create a map for quick lookup
        Map<Long, Task> taskMap = tasks.stream().collect(Collectors.toMap(Task::getId, t -> t));

        // Create ordering map from topological sort
        Map<Long, Integer> orderMap = new HashMap<>();
        for (int i = 0; i < topoOrder.size(); i++) {
            orderMap.put(topoOrder.get(i), i);
        }

        return tasks.stream()
                .sorted(Comparator.<Task>comparingDouble(t -> {
                            // Priority weight
                            int priorityWeight = PRIORITY_WEIGHT.getOrDefault(t.getPriority(), 50);

                            // Urgency factor: closer deadline = higher urgency
                            double urgencyFactor = 1.0;
                            Instant deadline = t.getDeadline() != null ? t.getDeadline() : t.getDueDate();
                            if (deadline != null) {
                                long daysUntilDeadline = ChronoUnit.DAYS.between(now, deadline);
                                if (daysUntilDeadline <= 0) {
                                    urgencyFactor = 10.0; // Overdue
                                } else if (daysUntilDeadline <= 1) {
                                    urgencyFactor = 5.0;
                                } else if (daysUntilDeadline <= 3) {
                                    urgencyFactor = 3.0;
                                } else if (daysUntilDeadline <= 7) {
                                    urgencyFactor = 2.0;
                                }
                            }

                            // Negate so higher scores come first (descending order)
                            return -(priorityWeight * urgencyFactor);
                        })
                        // Break ties with topological order (dependencies first)
                        .thenComparingInt(t -> orderMap.getOrDefault(t.getId(), Integer.MAX_VALUE))
                )
                .toList();
    }

    private Map<Long, Set<Long>> buildDependencyGraph(List<TaskDependency> dependencies) {
        Map<Long, Set<Long>> graph = new HashMap<>();
        for (TaskDependency dep : dependencies) {
            graph.computeIfAbsent(dep.getTask().getId(), k -> new HashSet<>())
                    .add(dep.getDependencyTask().getId());
        }
        return graph;
    }

    private Instant startOfWorkDay(LocalDate date, UserSchedulingPreferences prefs, ZoneId zoneId) {
        LocalDateTime dateTime = LocalDateTime.of(date, prefs.getWorkDayStartTime());
        return dateTime.atZone(zoneId).toInstant();
    }

    private Instant endOfWorkDay(LocalDate date, UserSchedulingPreferences prefs, ZoneId zoneId) {
        LocalDateTime dateTime = LocalDateTime.of(date, prefs.getWorkDayEndTime());
        return dateTime.atZone(zoneId).toInstant();
    }

    private boolean isWorkDay(LocalDate date, UserSchedulingPreferences prefs) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        String dayName = dayOfWeek.name();
        return prefs.getWorkDaysSet().contains(dayName);
    }

    private ZoneId resolveTimezone(String timezone) {
        if (timezone == null || timezone.trim().isEmpty()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(timezone.trim());
        } catch (Exception e) {
            log.warn("Invalid timezone '{}', falling back to UTC", timezone);
            return ZoneOffset.UTC;
        }
    }

    private String buildSchedulingReason(Task task, int dayOffset, int duration) {
        StringBuilder reason = new StringBuilder();
        reason.append("Scheduled based on ");
        reason.append(task.getPriority().name().toLowerCase()).append(" priority");

        Instant deadline = task.getDeadline() != null ? task.getDeadline() : task.getDueDate();
        if (deadline != null) {
            reason.append(" and deadline ").append(deadline);
        }

        if (dayOffset > 0) {
            reason.append(" (placed ").append(dayOffset).append(" days ahead due to availability)");
        }

        return reason.toString();
    }

    private SchedulingPlanResponse emptyPlan(String summary) {
        return new SchedulingPlanResponse(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Instant.now(),
                summary
        );
    }
}
