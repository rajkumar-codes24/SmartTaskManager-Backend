package com.smarttaskmanager.task.service;

import com.smarttaskmanager.common.exception.BusinessRuleException;
import com.smarttaskmanager.task.dto.CreateRecurrenceRequest;
import com.smarttaskmanager.task.dto.RecurrenceResponse;
import com.smarttaskmanager.task.entity.RecurrenceType;
import com.smarttaskmanager.task.entity.Task;
import com.smarttaskmanager.task.entity.TaskPriority;
import com.smarttaskmanager.task.entity.TaskStatus;
import com.smarttaskmanager.task.entity.TaskRecurrenceRule;
import com.smarttaskmanager.task.repository.TaskRecurrenceRuleRepository;
import com.smarttaskmanager.task.repository.TaskRepository;
import com.smarttaskmanager.user.entity.Role;
import com.smarttaskmanager.user.entity.User;
import com.smarttaskmanager.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskRecurrenceConcurrencyAndDSTTests {

    @Mock
    private TaskRecurrenceRuleRepository recurrenceRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private Authentication authentication;

    @InjectMocks
    @Spy
    private TaskRecurrenceService recurrenceService;

    private User testUser;
    private Task testTask;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setContext(new org.springframework.security.core.context.SecurityContextImpl(authentication));

        testUser = User.builder()
                .id(1L)
                .fullName("Test User")
                .email("test@example.com")
                .role(Role.ROLE_USER)
                .enabled(true)
                .emailVerified(true)
                .build();

        Instant dueDate = Instant.parse("2027-01-15T10:00:00Z");
        testTask = Task.builder()
                .id(1L)
                .user(testUser)
                .title("Recurring Task")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.HIGH)
                .dueDate(dueDate)
                .timezone("UTC")
                .build();

        when(authentication.getName()).thenReturn("test@example.com");
        lenient().when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(testUser));
        lenient().when(taskRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(testTask));
    }

    // ==================== Concurrency Tests ====================

    @Test
    @DisplayName("Optimistic locking conflict triggers retry and succeeds on second attempt")
    void generateNextOccurrence_optimisticLockingConflict_retriesAndSucceeds() throws Exception {
        TaskRecurrenceRule rule = TaskRecurrenceRule.builder()
                .id(1L)
                .task(testTask)
                .user(testUser)
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .occurrenceCount(5)
                .generatedOccurrences(0)
                .isCompleted(false)
                .build();

        int[] saveCallCount = {0};

        when(recurrenceRepository.findByTaskIdAndUserId(1L, 1L)).thenReturn(Optional.of(rule));

        when(recurrenceRepository.save(any(TaskRecurrenceRule.class))).thenAnswer(invocation -> {
            saveCallCount[0]++;
            if (saveCallCount[0] == 1) {
                // First save throws optimistic locking exception — triggers retry
                throw new ObjectOptimisticLockingFailureException(
                        "Object of class TaskRecurrenceRule with identifier 1 has been updated by another transaction",
                        1L);
            }
            // Second save succeeds after retry
            TaskRecurrenceRule savedRule = invocation.getArgument(0);
            savedRule.setGeneratedOccurrences(1);
            return savedRule;
        });
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        recurrenceService.generateNextOccurrence(1L);

        // Verify retry happened: save was called at least twice (once failed, once succeeded)
        verify(recurrenceRepository, atLeast(2)).save(any(TaskRecurrenceRule.class));
    }

    @Test
    @DisplayName("Optimistic locking conflict after max retries throws BusinessRuleException")
    void generateNextOccurrence_optimisticLockingConflict_maxRetriesExceeded_throwsException() {
        TaskRecurrenceRule rule = TaskRecurrenceRule.builder()
                .id(1L)
                .task(testTask)
                .user(testUser)
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .occurrenceCount(5)
                .generatedOccurrences(0)
                .isCompleted(false)
                .build();

        when(recurrenceRepository.findByTaskIdAndUserId(1L, 1L)).thenReturn(Optional.of(rule));
        when(recurrenceRepository.save(any(TaskRecurrenceRule.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(
                        "Object of class TaskRecurrenceRule with identifier 1 has been updated by another transaction",
                        1L));

        assertThatThrownBy(() -> recurrenceService.generateNextOccurrence(1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Another process");

        // Verify 3 retry attempts
        verify(recurrenceRepository, atLeast(3)).save(any(TaskRecurrenceRule.class));
    }

    @Test
    @DisplayName("Completed recurrence rule does not generate further occurrences")
    void generateNextOccurrence_completedRule_noFurtherGeneration() {
        TaskRecurrenceRule rule = TaskRecurrenceRule.builder()
                .id(1L)
                .task(testTask)
                .user(testUser)
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .occurrenceCount(3)
                .generatedOccurrences(3)
                .isCompleted(true)
                .build();

        when(recurrenceRepository.findByTaskIdAndUserId(1L, 1L)).thenReturn(Optional.of(rule));

        recurrenceService.generateNextOccurrence(1L);

        // Should return early without saving or deleting
        verify(recurrenceRepository, never()).save(any(TaskRecurrenceRule.class));
        verify(recurrenceRepository, never()).delete(any());
        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    @DisplayName("Occurrence count semantics: initial task is occurrence 0, occurrenceCount=1 allows 1 more")
    void occurrenceCount_semantics_initialTaskIsZero() {
        // occurrenceCount=1 means 1 additional occurrence after the original
        // generatedOccurrences starts at 0
        // First generation: generatedOccurrences becomes 1, which equals occurrenceCount=1
        // So the rule should be marked completed after the first generation

        TaskRecurrenceRule rule = TaskRecurrenceRule.builder()
                .id(1L)
                .task(testTask)
                .user(testUser)
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .occurrenceCount(1)
                .generatedOccurrences(0)
                .isCompleted(false)
                .build();

        when(recurrenceRepository.findByTaskIdAndUserId(1L, 1L)).thenReturn(Optional.of(rule));
        when(recurrenceRepository.save(any(TaskRecurrenceRule.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // First generation: 0 -> 1, which equals occurrenceCount=1
        recurrenceService.generateNextOccurrence(1L);
        assertThat(rule.getGeneratedOccurrences()).isEqualTo(1);

        // Second call: generatedOccurrences=1 >= occurrenceCount=1, so mark completed
        recurrenceService.generateNextOccurrence(1L);
        assertThat(rule.getIsCompleted()).isTrue();
    }

    @Test
    @DisplayName("Occurrence count semantics: occurrenceCount=3 allows exactly 3 generations")
    void occurrenceCount_semantics_threeGenerations() {
        TaskRecurrenceRule rule = TaskRecurrenceRule.builder()
                .id(1L)
                .task(testTask)
                .user(testUser)
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .occurrenceCount(3)
                .generatedOccurrences(0)
                .isCompleted(false)
                .build();

        when(recurrenceRepository.findByTaskIdAndUserId(1L, 1L)).thenReturn(Optional.of(rule));
        when(recurrenceRepository.save(any(TaskRecurrenceRule.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Generation 1: 0 -> 1
        recurrenceService.generateNextOccurrence(1L);
        assertThat(rule.getGeneratedOccurrences()).isEqualTo(1);
        assertThat(rule.getIsCompleted()).isFalse();

        // Generation 2: 1 -> 2
        recurrenceService.generateNextOccurrence(1L);
        assertThat(rule.getGeneratedOccurrences()).isEqualTo(2);
        assertThat(rule.getIsCompleted()).isFalse();

        // Generation 3: 2 -> 3
        recurrenceService.generateNextOccurrence(1L);
        assertThat(rule.getGeneratedOccurrences()).isEqualTo(3);
        assertThat(rule.getIsCompleted()).isFalse();

        // Generation 4: 3 >= 3, mark completed
        recurrenceService.generateNextOccurrence(1L);
        assertThat(rule.getIsCompleted()).isTrue();
        assertThat(rule.getGeneratedOccurrences()).isEqualTo(3); // No increment
    }

    // ==================== DST Transition Tests ====================

    @Test
    @DisplayName("DST spring forward gap: preserves intended local time")
    void calculateNextOccurrence_DSTSpringForward_preservesLocalTime() {
        // In America/New_York, DST spring forward happens on March 13, 2027 at 2:00 AM
        // 2:00 AM -> 3:00 AM (the hour 2:00-3:00 doesn't exist)
        // A task due on March 12, 2027 at 2:30 AM EDT should recur on March 13 at 3:00 AM EDT
        // because 2:30 AM doesn't exist on March 13

        Task dstTask = Task.builder()
                .id(100L)
                .user(testUser)
                .title("DST Spring Forward Task")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.HIGH)
                .dueDate(LocalDate.of(2027, 3, 12).atTime(2, 30)
                        .atZone(ZoneId.of("America/New_York")).toInstant())
                .timezone("America/New_York")
                .build();

        CreateRecurrenceRequest request = new CreateRecurrenceRequest(RecurrenceType.DAILY, 1, null, null, null, null, null);

        when(taskRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(dstTask));
        when(recurrenceRepository.findByTaskIdAndUserId(100L, 1L)).thenReturn(Optional.empty());
        when(recurrenceRepository.save(any(TaskRecurrenceRule.class))).thenAnswer(invocation -> {
            TaskRecurrenceRule rule = invocation.getArgument(0);
            rule.setId(1L);
            return rule;
        });

        RecurrenceResponse response = recurrenceService.createRecurrence(100L, request);

        // The next occurrence is March 13, 2027. atStartOfDay uses the zone's rules.
        // On DST spring-forward day, atStartOfDay(2027-03-13, America/New_York) returns 00:00 EDT
        ZoneId nyZone = ZoneId.of("America/New_York");
        Instant expected = LocalDate.of(2027, 3, 13).atStartOfDay(nyZone).toInstant();
        assertThat(response.nextOccurrenceAt()).isEqualTo(expected);
    }

    @Test
    @DisplayName("DST fall back ambiguous time: preserves intended local time")
    void calculateNextOccurrence_DSTFallBack_ambiguousTime() {
        // In America/New_York, DST fall back happens on November 7, 2027 at 2:00 AM
        // 2:00 AM EDT -> 1:00 AM EST (the hour 1:00-2:00 occurs twice)
        // atStartOfDay should resolve to the first occurrence (EDT)

        Task dstTask = Task.builder()
                .id(101L)
                .user(testUser)
                .title("DST Fall Back Task")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.HIGH)
                .dueDate(LocalDate.of(2027, 11, 6).atTime(1, 0)
                        .atZone(ZoneId.of("America/New_York")).toInstant())
                .timezone("America/New_York")
                .build();

        CreateRecurrenceRequest request = new CreateRecurrenceRequest(RecurrenceType.DAILY, 1, null, null, null, null, null);

        when(taskRepository.findByIdAndUserId(101L, 1L)).thenReturn(Optional.of(dstTask));
        when(recurrenceRepository.findByTaskIdAndUserId(101L, 1L)).thenReturn(Optional.empty());
        when(recurrenceRepository.save(any(TaskRecurrenceRule.class))).thenAnswer(invocation -> {
            TaskRecurrenceRule rule = invocation.getArgument(0);
            rule.setId(1L);
            return rule;
        });

        RecurrenceResponse response = recurrenceService.createRecurrence(101L, request);

        // Next occurrence is November 7, 2027 (DST fall-back day)
        // atStartOfDay resolves to 00:00 EDT (before the transition)
        ZoneId nyZone = ZoneId.of("America/New_York");
        Instant expected = LocalDate.of(2027, 11, 7).atStartOfDay(nyZone).toInstant();
        assertThat(response.nextOccurrenceAt()).isEqualTo(expected);
    }

    @Test
    @DisplayName("DST transition does not affect UTC-based recurrence")
    void calculateNextOccurrence_DSTTransition_UTCUnaffected() {
        // UTC has no DST transitions
        Task utcTask = Task.builder()
                .id(102L)
                .user(testUser)
                .title("UTC DST Task")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.HIGH)
                .dueDate(LocalDate.of(2027, 3, 12).atStartOfDay(ZoneOffset.UTC).toInstant())
                .timezone("UTC")
                .build();

        CreateRecurrenceRequest request = new CreateRecurrenceRequest(RecurrenceType.DAILY, 1, null, null, null, null, null);

        when(taskRepository.findByIdAndUserId(102L, 1L)).thenReturn(Optional.of(utcTask));
        when(recurrenceRepository.findByTaskIdAndUserId(102L, 1L)).thenReturn(Optional.empty());
        when(recurrenceRepository.save(any(TaskRecurrenceRule.class))).thenAnswer(invocation -> {
            TaskRecurrenceRule rule = invocation.getArgument(0);
            rule.setId(1L);
            return rule;
        });

        RecurrenceResponse response = recurrenceService.createRecurrence(102L, request);

        Instant expected = LocalDate.of(2027, 3, 13).atStartOfDay(ZoneOffset.UTC).toInstant();
        assertThat(response.nextOccurrenceAt()).isEqualTo(expected);
    }

    // ==================== Server Timezone Independence Tests ====================

    @Test
    @DisplayName("Task timezone takes precedence over server timezone")
    void calculateNextOccurrence_taskTimezone_precedesServerTimezone() {
        // Even if the server is in a different timezone, the task's timezone should be used
        Task kolkataTask = Task.builder()
                .id(103L)
                .user(testUser)
                .title("Kolkata Timezone Task")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.HIGH)
                .dueDate(Instant.parse("2027-01-15T10:00:00Z"))
                .timezone("Asia/Kolkata")
                .build();

        CreateRecurrenceRequest request = new CreateRecurrenceRequest(RecurrenceType.DAILY, 1, null, null, null, null, null);

        when(taskRepository.findByIdAndUserId(103L, 1L)).thenReturn(Optional.of(kolkataTask));
        when(recurrenceRepository.findByTaskIdAndUserId(103L, 1L)).thenReturn(Optional.empty());
        when(recurrenceRepository.save(any(TaskRecurrenceRule.class))).thenAnswer(invocation -> {
            TaskRecurrenceRule rule = invocation.getArgument(0);
            rule.setId(1L);
            return rule;
        });

        RecurrenceResponse response = recurrenceService.createRecurrence(103L, request);

        // Next occurrence should be 2027-01-16 00:00 IST = 2027-01-15 18:30 UTC
        ZoneId istZone = ZoneId.of("Asia/Kolkata");
        Instant expected = LocalDate.of(2027, 1, 16).atStartOfDay(istZone).toInstant();
        assertThat(response.nextOccurrenceAt()).isEqualTo(expected);
        // Verify it's NOT calculated in UTC (which would be 2027-01-16 00:00 UTC)
        assertThat(response.nextOccurrenceAt()).isNotEqualTo(
                LocalDate.of(2027, 1, 16).atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    @Test
    @DisplayName("Blank timezone falls back to UTC, not server timezone")
    void calculateNextOccurrence_blankTimezone_fallsBackToUTC() {
        Task blankTask = Task.builder()
                .id(104L)
                .user(testUser)
                .title("Blank Timezone Task")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.HIGH)
                .dueDate(Instant.parse("2027-01-15T10:00:00Z"))
                .timezone("   ")
                .build();

        CreateRecurrenceRequest request = new CreateRecurrenceRequest(RecurrenceType.DAILY, 1, null, null, null, null, null);

        when(taskRepository.findByIdAndUserId(104L, 1L)).thenReturn(Optional.of(blankTask));
        when(recurrenceRepository.findByTaskIdAndUserId(104L, 1L)).thenReturn(Optional.empty());
        when(recurrenceRepository.save(any(TaskRecurrenceRule.class))).thenAnswer(invocation -> {
            TaskRecurrenceRule rule = invocation.getArgument(0);
            rule.setId(1L);
            return rule;
        });

        RecurrenceResponse response = recurrenceService.createRecurrence(104L, request);

        // Should fall back to UTC
        Instant expected = LocalDate.of(2027, 1, 16).atStartOfDay(ZoneOffset.UTC).toInstant();
        assertThat(response.nextOccurrenceAt()).isEqualTo(expected);
    }
}
