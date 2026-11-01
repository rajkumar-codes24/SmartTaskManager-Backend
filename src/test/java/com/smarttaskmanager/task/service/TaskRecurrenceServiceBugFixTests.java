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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskRecurrenceServiceBugFixTests {

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

        // Task due on 2027-01-15 10:00 UTC
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

    // ==================== BUG 1: Occurrence Counting Tests ====================

    @Test
    @DisplayName("Generated occurrences starts at 0")
    void createRecurrence_generatedOccurrences_startsAtZero() {
        CreateRecurrenceRequest request = new CreateRecurrenceRequest(RecurrenceType.DAILY, 1, null, null, null, null, null);

        when(recurrenceRepository.findByTaskIdAndUserId(1L, 1L)).thenReturn(Optional.empty());
        when(recurrenceRepository.save(any(TaskRecurrenceRule.class))).thenAnswer(invocation -> {
            TaskRecurrenceRule rule = invocation.getArgument(0);
            rule.setId(1L);
            return rule;
        });

        RecurrenceResponse response = recurrenceService.createRecurrence(1L, request);

        assertThat(response.generatedOccurrences()).isZero();
    }

    @Test
    @DisplayName("Generated occurrences increments on each generateNextOccurrence call")
    void generateNextOccurrence_incrementsGeneratedOccurrences() {
        CreateRecurrenceRequest request = new CreateRecurrenceRequest(RecurrenceType.DAILY, 1, null, null, null, null, null);

        TaskRecurrenceRule rule = TaskRecurrenceRule.builder()
                .id(1L)
                .task(testTask)
                .user(testUser)
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .generatedOccurrences(0)
                .build();

        when(recurrenceRepository.findByTaskIdAndUserId(1L, 1L)).thenReturn(Optional.of(rule));
        when(recurrenceRepository.save(any(TaskRecurrenceRule.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // First generation
        recurrenceService.generateNextOccurrence(1L);
        assertThat(rule.getGeneratedOccurrences()).isEqualTo(1);

        // Second generation
        recurrenceService.generateNextOccurrence(1L);
        assertThat(rule.getGeneratedOccurrences()).isEqualTo(2);

        // Third generation
        recurrenceService.generateNextOccurrence(1L);
        assertThat(rule.getGeneratedOccurrences()).isEqualTo(3);
    }

    @Test
    @DisplayName("Max occurrence count stops generation and marks rule completed")
    void generateNextOccurrence_maxOccurrencesReached_marksCompleted() {
        TaskRecurrenceRule rule = TaskRecurrenceRule.builder()
                .id(1L)
                .task(testTask)
                .user(testUser)
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .occurrenceCount(3)
                .generatedOccurrences(3)
                .isCompleted(false)
                .build();

        when(recurrenceRepository.findByTaskIdAndUserId(1L, 1L)).thenReturn(Optional.of(rule));
        when(recurrenceRepository.save(any(TaskRecurrenceRule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        recurrenceService.generateNextOccurrence(1L);

        assertThat(rule.getIsCompleted()).isTrue();
        verify(recurrenceRepository).save(rule);
        verify(recurrenceRepository, never()).delete(any());
        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    @DisplayName("Max occurrence count allows generation below limit")
    void generateNextOccurrence_belowMaxOccurrences_generatesNext() {
        TaskRecurrenceRule rule = TaskRecurrenceRule.builder()
                .id(1L)
                .task(testTask)
                .user(testUser)
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .occurrenceCount(5)
                .generatedOccurrences(2)
                .build();

        when(recurrenceRepository.findByTaskIdAndUserId(1L, 1L)).thenReturn(Optional.of(rule));
        when(recurrenceRepository.save(any(TaskRecurrenceRule.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        recurrenceService.generateNextOccurrence(1L);

        assertThat(rule.getGeneratedOccurrences()).isEqualTo(3);
        verify(recurrenceRepository).save(rule);
        verify(taskRepository).save(testTask);
    }

    @Test
    @DisplayName("No max occurrence count allows unlimited generation")
    void generateNextOccurrence_noMaxCount_allowsUnlimited() {
        TaskRecurrenceRule rule = TaskRecurrenceRule.builder()
                .id(1L)
                .task(testTask)
                .user(testUser)
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .occurrenceCount(null)
                .generatedOccurrences(100)
                .build();

        when(recurrenceRepository.findByTaskIdAndUserId(1L, 1L)).thenReturn(Optional.of(rule));
        when(recurrenceRepository.save(any(TaskRecurrenceRule.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        recurrenceService.generateNextOccurrence(1L);

        assertThat(rule.getGeneratedOccurrences()).isEqualTo(101);
        verify(recurrenceRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Pause/resume does not reset occurrence count")
    void pauseResume_preservesGeneratedOccurrences() {
        CreateRecurrenceRequest request = new CreateRecurrenceRequest(RecurrenceType.DAILY, 1, null, null, null, null, null);

        TaskRecurrenceRule rule = TaskRecurrenceRule.builder()
                .id(1L)
                .task(testTask)
                .user(testUser)
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .generatedOccurrences(5)
                .isPaused(false)
                .build();

        when(recurrenceRepository.findByTaskIdAndUserId(1L, 1L)).thenReturn(Optional.of(rule));
        when(recurrenceRepository.save(any(TaskRecurrenceRule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Pause
        recurrenceService.pauseRecurrence(1L);
        assertThat(rule.getIsPaused()).isTrue();
        assertThat(rule.getGeneratedOccurrences()).isEqualTo(5);

        // Resume
        recurrenceService.resumeRecurrence(1L);
        assertThat(rule.getIsPaused()).isFalse();
        assertThat(rule.getGeneratedOccurrences()).isEqualTo(5);
    }

    @Test
    @DisplayName("Paused recurrence cannot generate next occurrence")
    void generateNextOccurrence_paused_throwsException() {
        TaskRecurrenceRule rule = TaskRecurrenceRule.builder()
                .id(1L)
                .task(testTask)
                .user(testUser)
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .isPaused(true)
                .generatedOccurrences(0)
                .build();

        when(recurrenceRepository.findByTaskIdAndUserId(1L, 1L)).thenReturn(Optional.of(rule));

        assertThatThrownBy(() -> recurrenceService.generateNextOccurrence(1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("paused");
    }

    // ==================== BUG 2: Timezone Handling Tests ====================

    @Test
    @DisplayName("Recurrence uses UTC timezone when task timezone is UTC")
    void calculateNextOccurrence_UTC_timezoneIsCorrect() {
        // Task due on 2027-01-15 10:00 UTC
        Task utcTask = Task.builder()
                .id(2L)
                .user(testUser)
                .title("UTC Task")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.HIGH)
                .dueDate(Instant.parse("2027-01-15T10:00:00Z"))
                .timezone("UTC")
                .build();

        CreateRecurrenceRequest request = new CreateRecurrenceRequest(RecurrenceType.DAILY, 1, null, null, null, null, null);

        when(taskRepository.findByIdAndUserId(2L, 1L)).thenReturn(Optional.of(utcTask));
        when(recurrenceRepository.findByTaskIdAndUserId(2L, 1L)).thenReturn(Optional.empty());
        when(recurrenceRepository.save(any(TaskRecurrenceRule.class))).thenAnswer(invocation -> {
            TaskRecurrenceRule rule = invocation.getArgument(0);
            rule.setId(1L);
            return rule;
        });

        RecurrenceResponse response = recurrenceService.createRecurrence(2L, request);

        // Next occurrence should be 2027-01-16 00:00 UTC (start of next day in UTC)
        Instant expected = LocalDate.of(2027, 1, 16).atStartOfDay(ZoneOffset.UTC).toInstant();
        assertThat(response.nextOccurrenceAt()).isEqualTo(expected);
    }

    @Test
    @DisplayName("Recurrence uses Asia/Kolkata timezone correctly")
    void calculateNextOccurrence_AsiaKolkata_timezoneIsCorrect() {
        // Task due on 2027-01-15 10:00 UTC = 2027-01-15 15:30 IST
        Task istTask = Task.builder()
                .id(3L)
                .user(testUser)
                .title("IST Task")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.HIGH)
                .dueDate(Instant.parse("2027-01-15T10:00:00Z"))
                .timezone("Asia/Kolkata")
                .build();

        CreateRecurrenceRequest request = new CreateRecurrenceRequest(RecurrenceType.DAILY, 1, null, null, null, null, null);

        when(taskRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(istTask));
        when(recurrenceRepository.findByTaskIdAndUserId(3L, 1L)).thenReturn(Optional.empty());
        when(recurrenceRepository.save(any(TaskRecurrenceRule.class))).thenAnswer(invocation -> {
            TaskRecurrenceRule rule = invocation.getArgument(0);
            rule.setId(1L);
            return rule;
        });

        RecurrenceResponse response = recurrenceService.createRecurrence(3L, request);

        // Next occurrence should be 2027-01-16 00:00 IST = 2027-01-15 18:30 UTC
        ZoneId istZone = ZoneId.of("Asia/Kolkata");
        Instant expected = LocalDate.of(2027, 1, 16).atStartOfDay(istZone).toInstant();
        assertThat(response.nextOccurrenceAt()).isEqualTo(expected);
    }

    @Test
    @DisplayName("Recurrence uses America/New_York timezone with DST")
    void calculateNextOccurrence_AmericaNewYork_handlesDST() {
        // Task due on 2027-07-15 (summer, EDT = UTC-4)
        Task edtTask = Task.builder()
                .id(4L)
                .user(testUser)
                .title("EDT Task")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.HIGH)
                .dueDate(Instant.parse("2027-07-15T10:00:00Z"))
                .timezone("America/New_York")
                .build();

        CreateRecurrenceRequest request = new CreateRecurrenceRequest(RecurrenceType.DAILY, 1, null, null, null, null, null);

        when(taskRepository.findByIdAndUserId(4L, 1L)).thenReturn(Optional.of(edtTask));
        when(recurrenceRepository.findByTaskIdAndUserId(4L, 1L)).thenReturn(Optional.empty());
        when(recurrenceRepository.save(any(TaskRecurrenceRule.class))).thenAnswer(invocation -> {
            TaskRecurrenceRule rule = invocation.getArgument(0);
            rule.setId(1L);
            return rule;
        });

        RecurrenceResponse response = recurrenceService.createRecurrence(4L, request);

        // Next occurrence should be 2027-07-16 00:00 EDT = 2027-07-16 04:00 UTC
        ZoneId nyZone = ZoneId.of("America/New_York");
        Instant expected = LocalDate.of(2027, 7, 16).atStartOfDay(nyZone).toInstant();
        assertThat(response.nextOccurrenceAt()).isEqualTo(expected);
    }

    @Test
    @DisplayName("Invalid timezone falls back to UTC")
    void calculateNextOccurrence_invalidTimezone_fallsBackToUTC() {
        Task invalidTask = Task.builder()
                .id(5L)
                .user(testUser)
                .title("Invalid TZ Task")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.HIGH)
                .dueDate(Instant.parse("2027-01-15T10:00:00Z"))
                .timezone("Invalid/Timezone")
                .build();

        CreateRecurrenceRequest request = new CreateRecurrenceRequest(RecurrenceType.DAILY, 1, null, null, null, null, null);

        when(taskRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(invalidTask));
        when(recurrenceRepository.findByTaskIdAndUserId(5L, 1L)).thenReturn(Optional.empty());
        when(recurrenceRepository.save(any(TaskRecurrenceRule.class))).thenAnswer(invocation -> {
            TaskRecurrenceRule rule = invocation.getArgument(0);
            rule.setId(1L);
            return rule;
        });

        RecurrenceResponse response = recurrenceService.createRecurrence(5L, request);

        // Should fall back to UTC
        Instant expected = LocalDate.of(2027, 1, 16).atStartOfDay(ZoneOffset.UTC).toInstant();
        assertThat(response.nextOccurrenceAt()).isEqualTo(expected);
    }

    @Test
    @DisplayName("Null timezone falls back to UTC")
    void calculateNextOccurrence_nullTimezone_fallsBackToUTC() {
        Task nullTask = Task.builder()
                .id(6L)
                .user(testUser)
                .title("Null TZ Task")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.HIGH)
                .dueDate(Instant.parse("2027-01-15T10:00:00Z"))
                .timezone(null)
                .build();

        CreateRecurrenceRequest request = new CreateRecurrenceRequest(RecurrenceType.DAILY, 1, null, null, null, null, null);

        when(taskRepository.findByIdAndUserId(6L, 1L)).thenReturn(Optional.of(nullTask));
        when(recurrenceRepository.findByTaskIdAndUserId(6L, 1L)).thenReturn(Optional.empty());
        when(recurrenceRepository.save(any(TaskRecurrenceRule.class))).thenAnswer(invocation -> {
            TaskRecurrenceRule rule = invocation.getArgument(0);
            rule.setId(1L);
            return rule;
        });

        RecurrenceResponse response = recurrenceService.createRecurrence(6L, request);

        Instant expected = LocalDate.of(2027, 1, 16).atStartOfDay(ZoneOffset.UTC).toInstant();
        assertThat(response.nextOccurrenceAt()).isEqualTo(expected);
    }

    // ==================== Month-end and Leap Year Tests ====================

    @Test
    @DisplayName("Monthly recurrence from Jan 31 handles Feb correctly")
    void monthlyRecurrence_jan31_toFeb_capsToLastDay() {
        // Task due on Jan 31, 2027
        Task janTask = Task.builder()
                .id(7L)
                .user(testUser)
                .title("Jan 31 Task")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.HIGH)
                .dueDate(LocalDate.of(2027, 1, 31).atStartOfDay(ZoneOffset.UTC).toInstant())
                .timezone("UTC")
                .build();

        CreateRecurrenceRequest request = new CreateRecurrenceRequest(RecurrenceType.MONTHLY, 1, null, null, null, null, null);

        when(taskRepository.findByIdAndUserId(7L, 1L)).thenReturn(Optional.of(janTask));
        when(recurrenceRepository.findByTaskIdAndUserId(7L, 1L)).thenReturn(Optional.empty());
        when(recurrenceRepository.save(any(TaskRecurrenceRule.class))).thenAnswer(invocation -> {
            TaskRecurrenceRule rule = invocation.getArgument(0);
            rule.setId(1L);
            return rule;
        });

        RecurrenceResponse response = recurrenceService.createRecurrence(7L, request);

        // Feb 2027 has 28 days, so next occurrence should be Feb 28
        Instant expected = LocalDate.of(2027, 2, 28).atStartOfDay(ZoneOffset.UTC).toInstant();
        assertThat(response.nextOccurrenceAt()).isEqualTo(expected);
    }

    @Test
    @DisplayName("Monthly recurrence from Jan 30 handles Feb correctly")
    void monthlyRecurrence_jan30_toFeb_capsToLastDay() {
        Task janTask = Task.builder()
                .id(8L)
                .user(testUser)
                .title("Jan 30 Task")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.HIGH)
                .dueDate(LocalDate.of(2027, 1, 30).atStartOfDay(ZoneOffset.UTC).toInstant())
                .timezone("UTC")
                .build();

        CreateRecurrenceRequest request = new CreateRecurrenceRequest(RecurrenceType.MONTHLY, 1, null, null, null, null, null);

        when(taskRepository.findByIdAndUserId(8L, 1L)).thenReturn(Optional.of(janTask));
        when(recurrenceRepository.findByTaskIdAndUserId(8L, 1L)).thenReturn(Optional.empty());
        when(recurrenceRepository.save(any(TaskRecurrenceRule.class))).thenAnswer(invocation -> {
            TaskRecurrenceRule rule = invocation.getArgument(0);
            rule.setId(1L);
            return rule;
        });

        RecurrenceResponse response = recurrenceService.createRecurrence(8L, request);

        // Feb 2027 has 28 days, so next occurrence should be Feb 28
        Instant expected = LocalDate.of(2027, 2, 28).atStartOfDay(ZoneOffset.UTC).toInstant();
        assertThat(response.nextOccurrenceAt()).isEqualTo(expected);
    }

    @Test
    @DisplayName("Yearly recurrence from Feb 29 (leap year) handles non-leap year")
    void yearlyRecurrence_feb29_leapYear_toNonLeapYear_usesFeb28() {
        // Task due on Feb 29, 2028 (leap year)
        Task leapTask = Task.builder()
                .id(9L)
                .user(testUser)
                .title("Leap Year Task")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.HIGH)
                .dueDate(LocalDate.of(2028, 2, 29).atStartOfDay(ZoneOffset.UTC).toInstant())
                .timezone("UTC")
                .build();

        CreateRecurrenceRequest request = new CreateRecurrenceRequest(RecurrenceType.YEARLY, 1, null, null, null, null, null);

        when(taskRepository.findByIdAndUserId(9L, 1L)).thenReturn(Optional.of(leapTask));
        when(recurrenceRepository.findByTaskIdAndUserId(9L, 1L)).thenReturn(Optional.empty());
        when(recurrenceRepository.save(any(TaskRecurrenceRule.class))).thenAnswer(invocation -> {
            TaskRecurrenceRule rule = invocation.getArgument(0);
            rule.setId(1L);
            return rule;
        });

        RecurrenceResponse response = recurrenceService.createRecurrence(9L, request);

        // 2029 is not a leap year, so next occurrence should be Feb 28, 2029
        Instant expected = LocalDate.of(2029, 2, 28).atStartOfDay(ZoneOffset.UTC).toInstant();
        assertThat(response.nextOccurrenceAt()).isEqualTo(expected);
    }

    @Test
    @DisplayName("Yearly recurrence from Feb 29 (leap year) to next leap year")
    void yearlyRecurrence_feb29_leapYear_toLeapYear_keepsFeb29() {
        // Task due on Feb 29, 2028 (leap year)
        Task leapTask = Task.builder()
                .id(10L)
                .user(testUser)
                .title("Leap Year Task 2")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.HIGH)
                .dueDate(LocalDate.of(2028, 2, 29).atStartOfDay(ZoneOffset.UTC).toInstant())
                .timezone("UTC")
                .build();

        CreateRecurrenceRequest request = new CreateRecurrenceRequest(RecurrenceType.YEARLY, 4, null, null, null, null, null);

        when(taskRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(leapTask));
        when(recurrenceRepository.findByTaskIdAndUserId(10L, 1L)).thenReturn(Optional.empty());
        when(recurrenceRepository.save(any(TaskRecurrenceRule.class))).thenAnswer(invocation -> {
            TaskRecurrenceRule rule = invocation.getArgument(0);
            rule.setId(1L);
            return rule;
        });

        RecurrenceResponse response = recurrenceService.createRecurrence(10L, request);

        // 2032 is a leap year, so next occurrence should be Feb 29, 2032
        Instant expected = LocalDate.of(2032, 2, 29).atStartOfDay(ZoneOffset.UTC).toInstant();
        assertThat(response.nextOccurrenceAt()).isEqualTo(expected);
    }

    @Test
    @DisplayName("Monthly recurrence from Mar 31 handles Apr correctly")
    void monthlyRecurrence_mar31_toApr_capsToLastDay() {
        Task marTask = Task.builder()
                .id(11L)
                .user(testUser)
                .title("Mar 31 Task")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.HIGH)
                .dueDate(LocalDate.of(2027, 3, 31).atStartOfDay(ZoneOffset.UTC).toInstant())
                .timezone("UTC")
                .build();

        CreateRecurrenceRequest request = new CreateRecurrenceRequest(RecurrenceType.MONTHLY, 1, null, null, null, null, null);

        when(taskRepository.findByIdAndUserId(11L, 1L)).thenReturn(Optional.of(marTask));
        when(recurrenceRepository.findByTaskIdAndUserId(11L, 1L)).thenReturn(Optional.empty());
        when(recurrenceRepository.save(any(TaskRecurrenceRule.class))).thenAnswer(invocation -> {
            TaskRecurrenceRule rule = invocation.getArgument(0);
            rule.setId(1L);
            return rule;
        });

        RecurrenceResponse response = recurrenceService.createRecurrence(11L, request);

        // April has 30 days
        Instant expected = LocalDate.of(2027, 4, 30).atStartOfDay(ZoneOffset.UTC).toInstant();
        assertThat(response.nextOccurrenceAt()).isEqualTo(expected);
    }

    // ==================== End Date Tests ====================

    @Test
    @DisplayName("End date prevents generation when next occurrence is after end date")
    void generateNextOccurrence_endDatePassed_marksCompleted() {
        TaskRecurrenceRule rule = TaskRecurrenceRule.builder()
                .id(1L)
                .task(testTask)
                .user(testUser)
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .occurrenceCount(null)
                .generatedOccurrences(0)
                .isCompleted(false)
                .endDate(Instant.parse("2027-01-15T00:00:00Z"))
                .build();

        when(recurrenceRepository.findByTaskIdAndUserId(1L, 1L)).thenReturn(Optional.of(rule));
        when(recurrenceRepository.save(any(TaskRecurrenceRule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Task due date is 2027-01-15, next daily occurrence is 2027-01-16 which is after end date 2027-01-15
        recurrenceService.generateNextOccurrence(1L);

        assertThat(rule.getIsCompleted()).isTrue();
        verify(recurrenceRepository).save(rule);
        verify(recurrenceRepository, never()).delete(any());
    }

    // ==================== Weekly Recurrence Tests ====================

    @Test
    @DisplayName("Weekly recurrence calculates next occurrence correctly")
    void weeklyRecurrence_nextOccurrence_isCorrect() {
        Task weekTask = Task.builder()
                .id(12L)
                .user(testUser)
                .title("Weekly Task")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.HIGH)
                .dueDate(LocalDate.of(2027, 1, 15).atStartOfDay(ZoneOffset.UTC).toInstant())
                .timezone("UTC")
                .build();

        CreateRecurrenceRequest request = new CreateRecurrenceRequest(RecurrenceType.WEEKLY, 2, null, null, null, null, null);

        when(taskRepository.findByIdAndUserId(12L, 1L)).thenReturn(Optional.of(weekTask));
        when(recurrenceRepository.findByTaskIdAndUserId(12L, 1L)).thenReturn(Optional.empty());
        when(recurrenceRepository.save(any(TaskRecurrenceRule.class))).thenAnswer(invocation -> {
            TaskRecurrenceRule rule = invocation.getArgument(0);
            rule.setId(1L);
            return rule;
        });

        RecurrenceResponse response = recurrenceService.createRecurrence(12L, request);

        // 2027-01-15 + 2 weeks = 2027-01-29
        Instant expected = LocalDate.of(2027, 1, 29).atStartOfDay(ZoneOffset.UTC).toInstant();
        assertThat(response.nextOccurrenceAt()).isEqualTo(expected);
    }

    // ==================== Every N Days Tests ====================

    @Test
    @DisplayName("Every N days recurrence calculates correctly")
    void everyNDaysRecurrence_nextOccurrence_isCorrect() {
        Task nDaysTask = Task.builder()
                .id(13L)
                .user(testUser)
                .title("Every 3 Days Task")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.HIGH)
                .dueDate(LocalDate.of(2027, 1, 15).atStartOfDay(ZoneOffset.UTC).toInstant())
                .timezone("UTC")
                .build();

        CreateRecurrenceRequest request = new CreateRecurrenceRequest(RecurrenceType.EVERY_N_DAYS, 3, null, null, null, null, null);

        when(taskRepository.findByIdAndUserId(13L, 1L)).thenReturn(Optional.of(nDaysTask));
        when(recurrenceRepository.findByTaskIdAndUserId(13L, 1L)).thenReturn(Optional.empty());
        when(recurrenceRepository.save(any(TaskRecurrenceRule.class))).thenAnswer(invocation -> {
            TaskRecurrenceRule rule = invocation.getArgument(0);
            rule.setId(1L);
            return rule;
        });

        RecurrenceResponse response = recurrenceService.createRecurrence(13L, request);

        // 2027-01-15 + 3 days = 2027-01-18
        Instant expected = LocalDate.of(2027, 1, 18).atStartOfDay(ZoneOffset.UTC).toInstant();
        assertThat(response.nextOccurrenceAt()).isEqualTo(expected);
    }
}
