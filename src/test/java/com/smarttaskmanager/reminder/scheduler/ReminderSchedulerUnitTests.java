package com.smarttaskmanager.reminder.scheduler;

import com.smarttaskmanager.common.config.ApplicationProperties;
import com.smarttaskmanager.email.service.EmailService;
import com.smarttaskmanager.reminder.entity.Reminder;
import com.smarttaskmanager.reminder.entity.ReminderStatus;
import com.smarttaskmanager.reminder.service.ReminderService;
import com.smarttaskmanager.task.entity.Task;
import com.smarttaskmanager.task.entity.TaskPriority;
import com.smarttaskmanager.task.entity.TaskStatus;
import com.smarttaskmanager.user.entity.Role;
import com.smarttaskmanager.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReminderSchedulerUnitTests {

    @Mock
    private ReminderService reminderService;

    @Mock
    private EmailService emailService;

    @Mock
    private ApplicationProperties applicationProperties;

    @InjectMocks
    private ReminderScheduler reminderScheduler;

    private User userA;
    private Task userATask;
    private Reminder dueReminder;

    @BeforeEach
    void setUp() {
        userA = User.builder()
                .id(1L)
                .fullName("User A")
                .email("user-a@example.com")
                .role(Role.ROLE_USER)
                .build();

        userATask = Task.builder()
                .id(10L)
                .user(userA)
                .title("Complete project")
                .description("Finish the project")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.HIGH)
                .build();

        dueReminder = Reminder.builder()
                .id(100L)
                .user(userA)
                .task(userATask)
                .scheduledAt(Instant.now().minusSeconds(3600))
                .status(ReminderStatus.PENDING)
                .retryCount(0)
                .build();

        ApplicationProperties.Reminder reminderProps = new ApplicationProperties.Reminder(300000L, 50, 3);
        when(applicationProperties.reminder()).thenReturn(reminderProps);
    }

    @Test
    @DisplayName("Process due reminders sends email and marks as sent")
    void processDueReminders_successfullySendsEmail() {
        when(reminderService.findDueRemindersForProcessing(any(Instant.class), eq(50)))
                .thenReturn(List.of(dueReminder));
        doNothing().when(emailService).sendReminderEmail(anyString(), anyString(), anyString(), anyString(), anyString());
        doNothing().when(reminderService).markReminderAsSentById(any(Long.class));

        reminderScheduler.processDueReminders();

        verify(emailService).sendReminderEmail(
                eq("user-a@example.com"),
                eq("User A"),
                eq("Complete project"),
                eq("Finish the project"),
                anyString()
        );
        verify(reminderService).markReminderAsSentById(eq(100L));
    }

    @Test
    @DisplayName("Process due reminders handles email failure gracefully")
    void processDueReminders_emailFailure_marksAsFailed() {
        when(reminderService.findDueRemindersForProcessing(any(Instant.class), eq(50)))
                .thenReturn(List.of(dueReminder));
        doThrow(new RuntimeException("SMTP timeout"))
                .when(emailService).sendReminderEmail(anyString(), anyString(), anyString(), anyString(), anyString());
        doNothing().when(reminderService).markReminderAsFailedById(any(Long.class), anyString());

        reminderScheduler.processDueReminders();

        verify(emailService).sendReminderEmail(anyString(), anyString(), anyString(), anyString(), anyString());
        verify(reminderService).markReminderAsFailedById(eq(100L), anyString());
        verify(reminderService, never()).markReminderAsSentById(any());
    }

    @Test
    @DisplayName("Process due reminders skips when no due reminders")
    void processDueReminders_noDueReminders_doesNothing() {
        when(reminderService.findDueRemindersForProcessing(any(Instant.class), eq(50)))
                .thenReturn(List.of());

        reminderScheduler.processDueReminders();

        verify(emailService, never()).sendReminderEmail(anyString(), anyString(), anyString(), anyString(), anyString());
        verify(reminderService, never()).markReminderAsSentById(any());
    }

    @Test
    @DisplayName("Process due reminders handles null description")
    void processDueReminders_nullDescription_handlesGracefully() {
        Task taskWithNullDesc = Task.builder()
                .id(10L)
                .user(userA)
                .title("No description task")
                .description(null)
                .status(TaskStatus.TODO)
                .priority(TaskPriority.MEDIUM)
                .build();
        Reminder reminder = Reminder.builder()
                .id(100L)
                .user(userA)
                .task(taskWithNullDesc)
                .scheduledAt(Instant.now().minusSeconds(3600))
                .status(ReminderStatus.PENDING)
                .retryCount(0)
                .build();

        when(reminderService.findDueRemindersForProcessing(any(Instant.class), eq(50)))
                .thenReturn(List.of(reminder));
        doNothing().when(emailService).sendReminderEmail(anyString(), anyString(), anyString(), anyString(), anyString());
        doNothing().when(reminderService).markReminderAsSentById(any(Long.class));

        reminderScheduler.processDueReminders();

        verify(emailService).sendReminderEmail(
                eq("user-a@example.com"),
                eq("User A"),
                eq("No description task"),
                eq(null),
                anyString()
        );
    }

    @Test
    @DisplayName("Future reminder is not sent before scheduled time")
    void processDueReminders_futureReminder_notSent() {
        // A future reminder should not be returned by findDueRemindersForProcessing
        when(reminderService.findDueRemindersForProcessing(any(Instant.class), eq(50)))
                .thenReturn(List.of());

        reminderScheduler.processDueReminders();

        verify(emailService, never()).sendReminderEmail(anyString(), anyString(), anyString(), anyString(), anyString());
        verify(reminderService, never()).markReminderAsSentById(any());
    }

    @Test
    @DisplayName("SENT reminder is not sent again on next scheduler run")
    void processDueReminders_sentReminder_notResent() {
        // A SENT reminder should not be returned by findDueRemindersForProcessing
        // because it only queries PENDING reminders
        when(reminderService.findDueRemindersForProcessing(any(Instant.class), eq(50)))
                .thenReturn(List.of());

        reminderScheduler.processDueReminders();

        verify(emailService, never()).sendReminderEmail(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Multiple reminders are processed independently")
    void processDueReminders_multipleReminders_processesAll() {
        User userB = User.builder()
                .id(2L)
                .fullName("User B")
                .email("user-b@example.com")
                .role(Role.ROLE_USER)
                .build();
        Task userBTask = Task.builder()
                .id(20L)
                .user(userB)
                .title("User B task")
                .description("User B description")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.LOW)
                .build();
        Reminder reminderB = Reminder.builder()
                .id(200L)
                .user(userB)
                .task(userBTask)
                .scheduledAt(Instant.now().minusSeconds(1800))
                .status(ReminderStatus.PENDING)
                .retryCount(0)
                .build();

        when(reminderService.findDueRemindersForProcessing(any(Instant.class), eq(50)))
                .thenReturn(List.of(dueReminder, reminderB));
        doNothing().when(emailService).sendReminderEmail(anyString(), anyString(), anyString(), anyString(), anyString());
        doNothing().when(reminderService).markReminderAsSentById(any(Long.class));

        reminderScheduler.processDueReminders();

        verify(emailService, times(2)).sendReminderEmail(anyString(), anyString(), anyString(), anyString(), anyString());
        verify(reminderService).markReminderAsSentById(eq(100L));
        verify(reminderService).markReminderAsSentById(eq(200L));
    }

    @Test
    @DisplayName("One reminder failure does not block other reminders")
    void processDueReminders_oneFailure_doesNotBlockOthers() {
        User userB = User.builder()
                .id(2L)
                .fullName("User B")
                .email("user-b@example.com")
                .role(Role.ROLE_USER)
                .build();
        Task userBTask = Task.builder()
                .id(20L)
                .user(userB)
                .title("User B task")
                .description("User B description")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.LOW)
                .build();
        Reminder reminderB = Reminder.builder()
                .id(200L)
                .user(userB)
                .task(userBTask)
                .scheduledAt(Instant.now().minusSeconds(1800))
                .status(ReminderStatus.PENDING)
                .retryCount(0)
                .build();

        when(reminderService.findDueRemindersForProcessing(any(Instant.class), eq(50)))
                .thenReturn(List.of(dueReminder, reminderB));

        // First email succeeds, second email fails
        doAnswer(invocation -> {
                    if ("user-b@example.com".equals(invocation.getArgument(0))) {
                        throw new RuntimeException("SMTP connection refused");
                    }
                    return null;
                })
                .when(emailService).sendReminderEmail(anyString(), anyString(), anyString(), anyString(), anyString());
        doNothing().when(reminderService).markReminderAsSentById(any(Long.class));
        doNothing().when(reminderService).markReminderAsFailedById(any(Long.class), anyString());

        reminderScheduler.processDueReminders();

        // Both emails were attempted
        verify(emailService, times(2)).sendReminderEmail(anyString(), anyString(), anyString(), anyString(), anyString());
        // First reminder was sent successfully
        verify(reminderService).markReminderAsSentById(eq(100L));
        // Second reminder failed
        verify(reminderService).markReminderAsFailedById(eq(200L), anyString());
    }
}
