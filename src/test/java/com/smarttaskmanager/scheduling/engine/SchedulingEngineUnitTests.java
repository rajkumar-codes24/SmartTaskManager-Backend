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
import com.smarttaskmanager.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the SchedulingEngine algorithm.
 * Uses real entities but no database — all data is constructed in memory.
 */
@SpringBootTest
@ActiveProfiles("test")
class SchedulingEngineUnitTests {

    @Autowired
    private SchedulingEngine schedulingEngine;

    private User testUser;
    private UserSchedulingPreferences preferences;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .fullName("Test User")
                .email("test@example.com")
                .passwordHash("hashed")
                .enabled(true)
                .emailVerified(true)
                .build();

        preferences = UserSchedulingPreferences.builder()
                .id(1L)
                .user(testUser)
                .workDayStartTime(LocalTime.of(9, 0))
                .workDayEndTime(LocalTime.of(17, 0))
                .workDaysOfWeek("MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY")
                .defaultTaskDurationMinutes(60)
                .breakDurationMinutes(15)
                .timezone("UTC")
                .build();
    }

    private Task createTask(Long id, String title, TaskPriority priority, TaskStatus status, Instant dueDate, Integer duration) {
        return Task.builder()
                .id(id)
                .user(testUser)
                .title(title)
                .status(status)
                .priority(priority)
                .dueDate(dueDate)
                .estimatedDurationMinutes(duration)
                .timezone("UTC")
                .build();
    }

    @Test
    @DisplayName("Empty task list returns empty plan")
    void generatePlan_emptyTasks_returnsEmptyPlan() {
        SchedulingPlanResponse plan = schedulingEngine.generatePlan(List.of(), List.of(), preferences);

        assertThat(plan.scheduledTasks()).isEmpty();
        assertThat(plan.conflicts()).isEmpty();
        assertThat(plan.deadlineRisks()).isEmpty();
        assertThat(plan.unschedulableTaskIds()).isEmpty();
        assertThat(plan.summary()).contains("No eligible tasks");
    }

    @Test
    @DisplayName("Completed and cancelled tasks are excluded")
    void generatePlan_completedTasks_excluded() {
        Instant now = Instant.now();
        List<Task> tasks = List.of(
                createTask(1L, "Completed", TaskPriority.HIGH, TaskStatus.COMPLETED, now.plus(1, ChronoUnit.DAYS), 60),
                createTask(2L, "Cancelled", TaskPriority.HIGH, TaskStatus.CANCELLED, now.plus(1, ChronoUnit.DAYS), 60)
        );

        SchedulingPlanResponse plan = schedulingEngine.generatePlan(tasks, List.of(), preferences);

        assertThat(plan.scheduledTasks()).isEmpty();
    }

    @Test
    @DisplayName("Single task is scheduled within working hours")
    void generatePlan_singleTask_scheduledWithinWorkingHours() {
        Instant now = Instant.now();
        List<Task> tasks = List.of(
                createTask(1L, "Single Task", TaskPriority.HIGH, TaskStatus.TODO, now.plus(7, ChronoUnit.DAYS), 60)
        );

        SchedulingPlanResponse plan = schedulingEngine.generatePlan(tasks, List.of(), preferences);

        assertThat(plan.scheduledTasks()).hasSize(1);
        ScheduledTaskSlot slot = plan.scheduledTasks().get(0);
        assertThat(slot.taskId()).isEqualTo(1L);
        assertThat(slot.scheduledStartAt()).isNotNull();
        assertThat(slot.scheduledEndAt()).isNotNull();
        assertThat(slot.scheduledEndAt()).isAfter(slot.scheduledStartAt());
        assertThat(slot.estimatedDurationMinutes()).isEqualTo(60);
    }

    @Test
    @DisplayName("Higher priority tasks are scheduled first")
    void generatePlan_multiplePriorities_higherPriorityFirst() {
        Instant now = Instant.now();
        List<Task> tasks = List.of(
                createTask(1L, "Low Priority", TaskPriority.LOW, TaskStatus.TODO, now.plus(7, ChronoUnit.DAYS), 60),
                createTask(2L, "Urgent Priority", TaskPriority.URGENT, TaskStatus.TODO, now.plus(7, ChronoUnit.DAYS), 60),
                createTask(3L, "Medium Priority", TaskPriority.MEDIUM, TaskStatus.TODO, now.plus(7, ChronoUnit.DAYS), 60)
        );

        SchedulingPlanResponse plan = schedulingEngine.generatePlan(tasks, List.of(), preferences);

        assertThat(plan.scheduledTasks()).hasSize(3);
        // Urgent should be scheduled first (earliest start time)
        ScheduledTaskSlot first = plan.scheduledTasks().get(0);
        assertThat(first.taskId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Tasks with closer deadlines have higher urgency")
    void generatePlan_closerDeadline_scheduledFirst() {
        Instant now = Instant.now();
        List<Task> tasks = List.of(
                createTask(1L, "Far Deadline", TaskPriority.HIGH, TaskStatus.TODO, now.plus(30, ChronoUnit.DAYS), 60),
                createTask(2L, "Near Deadline", TaskPriority.HIGH, TaskStatus.TODO, now.plus(1, ChronoUnit.DAYS), 60)
        );

        SchedulingPlanResponse plan = schedulingEngine.generatePlan(tasks, List.of(), preferences);

        assertThat(plan.scheduledTasks()).hasSize(2);
        ScheduledTaskSlot first = plan.scheduledTasks().get(0);
        assertThat(first.taskId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Dependencies are respected in scheduling order")
    void generatePlan_withDependencies_dependencyScheduledFirst() {
        Instant now = Instant.now();
        Task taskA = createTask(1L, "Task A (dependency)", TaskPriority.HIGH, TaskStatus.TODO, now.plus(7, ChronoUnit.DAYS), 60);
        Task taskB = createTask(2L, "Task B (depends on A)", TaskPriority.HIGH, TaskStatus.TODO, now.plus(7, ChronoUnit.DAYS), 60);

        // Task B depends on Task A
        TaskDependency dep = TaskDependency.builder()
                .task(taskB)
                .dependencyTask(taskA)
                .user(testUser)
                .build();

        SchedulingPlanResponse plan = schedulingEngine.generatePlan(
                List.of(taskA, taskB), List.of(dep), preferences
        );

        assertThat(plan.scheduledTasks()).hasSize(2);
        // Task A should be scheduled before Task B
        ScheduledTaskSlot slotA = plan.scheduledTasks().stream()
                .filter(s -> s.taskId() == 1L).findFirst().orElseThrow();
        ScheduledTaskSlot slotB = plan.scheduledTasks().stream()
                .filter(s -> s.taskId() == 2L).findFirst().orElseThrow();
        assertThat(slotA.scheduledStartAt()).isBeforeOrEqualTo(slotB.scheduledStartAt());
    }

    @Test
    @DisplayName("Deadline risks are detected for overdue tasks")
    void detectDeadlineRisks_overdueTask_detected() {
        Instant now = Instant.now();
        List<Task> tasks = List.of(
                createTask(1L, "Overdue Task", TaskPriority.HIGH, TaskStatus.TODO, now.minus(1, ChronoUnit.DAYS), 60)
        );

        List<DeadlineRisk> risks = schedulingEngine.detectDeadlineRisks(tasks);

        assertThat(risks).hasSize(1);
        assertThat(risks.get(0).taskId()).isEqualTo(1L);
        assertThat(risks.get(0).reason()).contains("past its deadline");
    }

    @Test
    @DisplayName("Completed tasks are not flagged as deadline risks")
    void detectDeadlineRisks_completedTask_notFlagged() {
        Instant now = Instant.now();
        List<Task> tasks = List.of(
                createTask(1L, "Completed Overdue", TaskPriority.HIGH, TaskStatus.COMPLETED, now.minus(1, ChronoUnit.DAYS), 60)
        );

        List<DeadlineRisk> risks = schedulingEngine.detectDeadlineRisks(tasks);

        assertThat(risks).isEmpty();
    }

    @Test
    @DisplayName("Tasks without deadlines are not flagged as risks")
    void detectDeadlineRisks_noDeadline_notFlagged() {
        Instant now = Instant.now();
        List<Task> tasks = List.of(
                createTask(1L, "No Deadline", TaskPriority.HIGH, TaskStatus.TODO, null, 60)
        );

        List<DeadlineRisk> risks = schedulingEngine.detectDeadlineRisks(tasks);

        assertThat(risks).isEmpty();
    }

    @Test
    @DisplayName("Conflicts are detected between overlapping tasks")
    void detectTaskConflicts_overlappingTasks_detected() {
        Instant now = Instant.now();
        Instant start1 = now.plus(1, ChronoUnit.HOURS);
        Instant end1 = now.plus(2, ChronoUnit.HOURS);
        Instant start2 = now.plus(1, ChronoUnit.HOURS).plus(30, ChronoUnit.MINUTES);
        Instant end2 = now.plus(3, ChronoUnit.HOURS);

        Task task1 = createTask(1L, "Task 1", TaskPriority.HIGH, TaskStatus.TODO, now.plus(7, ChronoUnit.DAYS), 60);
        task1.setScheduledStartAt(start1);
        task1.setScheduledEndAt(end1);

        Task task2 = createTask(2L, "Task 2", TaskPriority.HIGH, TaskStatus.TODO, now.plus(7, ChronoUnit.DAYS), 120);
        task2.setScheduledStartAt(start2);
        task2.setScheduledEndAt(end2);

        List<SchedulingConflict> conflicts = schedulingEngine.detectTaskConflicts(List.of(task1, task2));

        assertThat(conflicts).hasSize(1);
        assertThat(conflicts.get(0).taskId1()).isIn(1L, 2L);
        assertThat(conflicts.get(0).taskId2()).isIn(1L, 2L);
    }

    @Test
    @DisplayName("No conflicts for non-overlapping tasks")
    void detectTaskConflicts_nonOverlappingTasks_noConflicts() {
        Instant now = Instant.now();
        Instant start1 = now.plus(1, ChronoUnit.HOURS);
        Instant end1 = now.plus(2, ChronoUnit.HOURS);
        Instant start2 = now.plus(3, ChronoUnit.HOURS);
        Instant end2 = now.plus(4, ChronoUnit.HOURS);

        Task task1 = createTask(1L, "Task 1", TaskPriority.HIGH, TaskStatus.TODO, now.plus(7, ChronoUnit.DAYS), 60);
        task1.setScheduledStartAt(start1);
        task1.setScheduledEndAt(end1);

        Task task2 = createTask(2L, "Task 2", TaskPriority.HIGH, TaskStatus.TODO, now.plus(7, ChronoUnit.DAYS), 60);
        task2.setScheduledStartAt(start2);
        task2.setScheduledEndAt(end2);

        List<SchedulingConflict> conflicts = schedulingEngine.detectTaskConflicts(List.of(task1, task2));

        assertThat(conflicts).isEmpty();
    }

    @Test
    @DisplayName("Default duration is used when task has no estimated duration")
    void generatePlan_noEstimatedDuration_usesDefault() {
        Instant now = Instant.now();
        List<Task> tasks = List.of(
                createTask(1L, "No Duration", TaskPriority.HIGH, TaskStatus.TODO, now.plus(7, ChronoUnit.DAYS), null)
        );

        SchedulingPlanResponse plan = schedulingEngine.generatePlan(tasks, List.of(), preferences);

        assertThat(plan.scheduledTasks()).hasSize(1);
        assertThat(plan.scheduledTasks().get(0).estimatedDurationMinutes()).isEqualTo(60);
    }

    @Test
    @DisplayName("Scheduling respects timezone")
    void generatePlan_withTimezone_schedulesCorrectly() {
        preferences.setTimezone("Asia/Kolkata");
        Instant now = Instant.now();
        List<Task> tasks = List.of(
                createTask(1L, "Timezone Task", TaskPriority.HIGH, TaskStatus.TODO, now.plus(7, ChronoUnit.DAYS), 60)
        );

        SchedulingPlanResponse plan = schedulingEngine.generatePlan(tasks, List.of(), preferences);

        assertThat(plan.scheduledTasks()).hasSize(1);
        assertThat(plan.scheduledTasks().get(0).scheduledStartAt()).isNotNull();
    }

    @Test
    @DisplayName("Invalid timezone falls back to UTC")
    void generatePlan_invalidTimezone_fallbackToUTC() {
        preferences.setTimezone("Invalid/Timezone");
        Instant now = Instant.now();
        List<Task> tasks = List.of(
                createTask(1L, "Invalid TZ Task", TaskPriority.HIGH, TaskStatus.TODO, now.plus(7, ChronoUnit.DAYS), 60)
        );

        SchedulingPlanResponse plan = schedulingEngine.generatePlan(tasks, List.of(), preferences);

        assertThat(plan.scheduledTasks()).hasSize(1);
    }

    @Test
    @DisplayName("Tasks scheduled on work days only")
    void generatePlan_workDaysOnly_skipsNonWorkDays() {
        preferences.setWorkDaysOfWeek("MONDAY");
        Instant now = Instant.now();
        List<Task> tasks = List.of(
                createTask(1L, "Monday Only", TaskPriority.HIGH, TaskStatus.TODO, now.plus(14, ChronoUnit.DAYS), 60)
        );

        SchedulingPlanResponse plan = schedulingEngine.generatePlan(tasks, List.of(), preferences);

        assertThat(plan.scheduledTasks()).hasSize(1);
        // The scheduled start should be on a Monday
        ScheduledTaskSlot slot = plan.scheduledTasks().get(0);
        assertThat(slot.scheduledStartAt()).isNotNull();
    }
}
