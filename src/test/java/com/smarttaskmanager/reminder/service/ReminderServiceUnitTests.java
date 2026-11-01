package com.smarttaskmanager.reminder.service;

import com.smarttaskmanager.common.exception.BusinessRuleException;
import com.smarttaskmanager.common.exception.ResourceNotFoundException;
import com.smarttaskmanager.common.exception.UnauthorizedAccessException;
import com.smarttaskmanager.reminder.dto.CreateReminderRequest;
import com.smarttaskmanager.reminder.dto.ReminderResponse;
import com.smarttaskmanager.reminder.dto.UpdateReminderRequest;
import com.smarttaskmanager.reminder.entity.Reminder;
import com.smarttaskmanager.reminder.entity.ReminderStatus;
import com.smarttaskmanager.reminder.repository.ReminderRepository;
import com.smarttaskmanager.task.entity.Task;
import com.smarttaskmanager.task.entity.TaskPriority;
import com.smarttaskmanager.task.entity.TaskStatus;
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
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReminderServiceUnitTests {

    @Mock
    private ReminderRepository reminderRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ReminderService reminderService;

    private User userA;
    private Task userATask;
    private Reminder userAReminder;

    @BeforeEach
    void setUp() {
        userA = User.builder()
                .id(1L)
                .fullName("User A")
                .email("user-a@example.com")
                .passwordHash("encodedPassword")
                .role(Role.ROLE_USER)
                .enabled(true)
                .emailVerified(true)
                .build();

        userATask = Task.builder()
                .id(10L)
                .user(userA)
                .title("Complete project")
                .description("Finish the project")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.HIGH)
                .dueDate(Instant.parse("2027-12-31T23:59:59Z"))
                .build();

        userAReminder = Reminder.builder()
                .id(100L)
                .user(userA)
                .task(userATask)
                .scheduledAt(Instant.parse("2027-06-01T09:00:00Z"))
                .status(ReminderStatus.PENDING)
                .retryCount(0)
                .createdAt(Instant.parse("2027-01-01T00:00:00Z"))
                .build();
    }

    private void setAuthenticatedUser(String email) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(email);
        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(context);
    }

    private void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    // ==================== Create Reminder Tests ====================

    @Test
    @DisplayName("Create reminder with valid request")
    void createReminder_validRequest_returnsResponse() {
        setAuthenticatedUser("user-a@example.com");

        Instant futureTime = Instant.now().plusSeconds(3600);
        CreateReminderRequest request = new CreateReminderRequest(10L, futureTime);

        when(userRepository.findByEmailIgnoreCase("user-a@example.com")).thenReturn(Optional.of(userA));
        when(taskRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(userATask));
        when(reminderRepository.save(any(Reminder.class))).thenAnswer(invocation -> {
            Reminder r = invocation.getArgument(0);
            r.setId(100L);
            r.setCreatedAt(Instant.now());
            return r;
        });

        ReminderResponse response = reminderService.createReminder(request);

        assertThat(response).isNotNull();
        assertThat(response.taskId()).isEqualTo(10L);
        assertThat(response.taskTitle()).isEqualTo("Complete project");
        assertThat(response.status()).isEqualTo(ReminderStatus.PENDING);
        assertThat(response.retryCount()).isZero();
        verify(reminderRepository).save(any(Reminder.class));
        clearAuthentication();
    }

    @Test
    @DisplayName("Create reminder for non-existent task throws ResourceNotFoundException")
    void createReminder_nonExistentTask_throwsException() {
        setAuthenticatedUser("user-a@example.com");

        Instant futureTime = Instant.now().plusSeconds(3600);
        CreateReminderRequest request = new CreateReminderRequest(999L, futureTime);

        when(userRepository.findByEmailIgnoreCase("user-a@example.com")).thenReturn(Optional.of(userA));
        when(taskRepository.findByIdAndUserId(999L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reminderService.createReminder(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Task");

        verify(reminderRepository, never()).save(any());
        clearAuthentication();
    }

    @Test
    @DisplayName("Create reminder for another user's task throws UnauthorizedAccessException")
    void createReminder_anotherUserTask_throwsUnauthorizedAccess() {
        setAuthenticatedUser("user-a@example.com");

        User userB = User.builder()
                .id(2L)
                .email("user-b@example.com")
                .role(Role.ROLE_USER)
                .build();
        Task userBTask = Task.builder()
                .id(20L)
                .user(userB)
                .title("User B's task")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.MEDIUM)
                .build();

        Instant futureTime = Instant.now().plusSeconds(3600);
        CreateReminderRequest request = new CreateReminderRequest(20L, futureTime);

        when(userRepository.findByEmailIgnoreCase("user-a@example.com")).thenReturn(Optional.of(userA));
        when(taskRepository.findByIdAndUserId(20L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reminderService.createReminder(request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(reminderRepository, never()).save(any());
        clearAuthentication();
    }

    @Test
    @DisplayName("Create reminder with past scheduled date throws BusinessRuleException")
    void createReminder_pastDate_throwsBusinessRuleException() {
        setAuthenticatedUser("user-a@example.com");

        Instant pastTime = Instant.now().minusSeconds(3600);
        CreateReminderRequest request = new CreateReminderRequest(10L, pastTime);

        when(userRepository.findByEmailIgnoreCase("user-a@example.com")).thenReturn(Optional.of(userA));
        when(taskRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(userATask));

        assertThatThrownBy(() -> reminderService.createReminder(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("future");

        verify(reminderRepository, never()).save(any());
        clearAuthentication();
    }

    // ==================== Get Reminder Tests ====================

    @Test
    @DisplayName("Get reminder by ID returns response")
    void getReminderById_existingReminder_returnsResponse() {
        setAuthenticatedUser("user-a@example.com");

        when(userRepository.findByEmailIgnoreCase("user-a@example.com")).thenReturn(Optional.of(userA));
        when(reminderRepository.findById(100L)).thenReturn(Optional.of(userAReminder));

        ReminderResponse response = reminderService.getReminderById(100L);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.taskId()).isEqualTo(10L);
        assertThat(response.taskTitle()).isEqualTo("Complete project");
        assertThat(response.status()).isEqualTo(ReminderStatus.PENDING);
        clearAuthentication();
    }

    @Test
    @DisplayName("Get reminder by ID not found throws ResourceNotFoundException")
    void getReminderById_notFound_throwsException() {
        setAuthenticatedUser("user-a@example.com");

        when(userRepository.findByEmailIgnoreCase("user-a@example.com")).thenReturn(Optional.of(userA));
        when(reminderRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reminderService.getReminderById(999L))
                .isInstanceOf(ResourceNotFoundException.class);
        clearAuthentication();
    }

    @Test
    @DisplayName("Get reminder owned by another user throws UnauthorizedAccessException")
    void getReminderById_anotherUser_throwsUnauthorizedAccess() {
        setAuthenticatedUser("user-a@example.com");

        User userB = User.builder()
                .id(2L)
                .email("user-b@example.com")
                .role(Role.ROLE_USER)
                .build();
        Task userBTask = Task.builder()
                .id(20L)
                .user(userB)
                .title("User B's task")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.MEDIUM)
                .build();
        Reminder userBReminder = Reminder.builder()
                .id(200L)
                .user(userB)
                .task(userBTask)
                .scheduledAt(Instant.now().plusSeconds(3600))
                .status(ReminderStatus.PENDING)
                .retryCount(0)
                .build();

        when(userRepository.findByEmailIgnoreCase("user-a@example.com")).thenReturn(Optional.of(userA));
        when(reminderRepository.findById(200L)).thenReturn(Optional.of(userBReminder));

        assertThatThrownBy(() -> reminderService.getReminderById(200L))
                .isInstanceOf(UnauthorizedAccessException.class);
        clearAuthentication();
    }

    // ==================== Update Reminder Tests ====================

    @Test
    @DisplayName("Update reminder scheduled time")
    void updateReminder_scheduledTime_returnsUpdatedResponse() {
        setAuthenticatedUser("user-a@example.com");

        Instant newTime = Instant.now().plusSeconds(7200);
        UpdateReminderRequest request = new UpdateReminderRequest(newTime, null);

        when(userRepository.findByEmailIgnoreCase("user-a@example.com")).thenReturn(Optional.of(userA));
        when(reminderRepository.findById(100L)).thenReturn(Optional.of(userAReminder));
        when(reminderRepository.save(any(Reminder.class))).thenReturn(userAReminder);

        ReminderResponse response = reminderService.updateReminder(100L, request);

        assertThat(response).isNotNull();
        assertThat(response.scheduledAt()).isEqualTo(newTime);
        verify(reminderRepository).save(any(Reminder.class));
        clearAuthentication();
    }

    @Test
    @DisplayName("Update SENT reminder throws BusinessRuleException")
    void updateReminder_sentReminder_throwsBusinessRuleException() {
        setAuthenticatedUser("user-a@example.com");

        Reminder sentReminder = Reminder.builder()
                .id(100L)
                .user(userA)
                .task(userATask)
                .scheduledAt(Instant.now().plusSeconds(3600))
                .status(ReminderStatus.SENT)
                .sentAt(Instant.now())
                .retryCount(0)
                .build();

        UpdateReminderRequest request = new UpdateReminderRequest(Instant.now().plusSeconds(7200), null);

        when(userRepository.findByEmailIgnoreCase("user-a@example.com")).thenReturn(Optional.of(userA));
        when(reminderRepository.findById(100L)).thenReturn(Optional.of(sentReminder));

        assertThatThrownBy(() -> reminderService.updateReminder(100L, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("SENT");

        verify(reminderRepository, never()).save(any());
        clearAuthentication();
    }

    @Test
    @DisplayName("Update CANCELLED reminder throws BusinessRuleException")
    void updateReminder_cancelledReminder_throwsBusinessRuleException() {
        setAuthenticatedUser("user-a@example.com");

        Reminder cancelledReminder = Reminder.builder()
                .id(100L)
                .user(userA)
                .task(userATask)
                .scheduledAt(Instant.now().plusSeconds(3600))
                .status(ReminderStatus.CANCELLED)
                .retryCount(0)
                .build();

        UpdateReminderRequest request = new UpdateReminderRequest(Instant.now().plusSeconds(7200), null);

        when(userRepository.findByEmailIgnoreCase("user-a@example.com")).thenReturn(Optional.of(userA));
        when(reminderRepository.findById(100L)).thenReturn(Optional.of(cancelledReminder));

        assertThatThrownBy(() -> reminderService.updateReminder(100L, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("CANCELLED");

        verify(reminderRepository, never()).save(any());
        clearAuthentication();
    }

    // ==================== Delete Reminder Tests ====================

    @Test
    @DisplayName("Delete reminder successfully")
    void deleteReminder_existingReminder_deletesSuccessfully() {
        setAuthenticatedUser("user-a@example.com");

        when(userRepository.findByEmailIgnoreCase("user-a@example.com")).thenReturn(Optional.of(userA));
        when(reminderRepository.findById(100L)).thenReturn(Optional.of(userAReminder));

        reminderService.deleteReminder(100L);

        verify(reminderRepository).delete(userAReminder);
        clearAuthentication();
    }

    @Test
    @DisplayName("Delete reminder owned by another user throws UnauthorizedAccessException")
    void deleteReminder_anotherUser_throwsUnauthorizedAccess() {
        setAuthenticatedUser("user-a@example.com");

        User userB = User.builder()
                .id(2L)
                .email("user-b@example.com")
                .role(Role.ROLE_USER)
                .build();
        Task userBTask = Task.builder()
                .id(20L)
                .user(userB)
                .title("User B's task")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.MEDIUM)
                .build();
        Reminder userBReminder = Reminder.builder()
                .id(200L)
                .user(userB)
                .task(userBTask)
                .scheduledAt(Instant.now().plusSeconds(3600))
                .status(ReminderStatus.PENDING)
                .retryCount(0)
                .build();

        when(userRepository.findByEmailIgnoreCase("user-a@example.com")).thenReturn(Optional.of(userA));
        when(reminderRepository.findById(200L)).thenReturn(Optional.of(userBReminder));

        assertThatThrownBy(() -> reminderService.deleteReminder(200L))
                .isInstanceOf(UnauthorizedAccessException.class);

        verify(reminderRepository, never()).delete(any());
        clearAuthentication();
    }

    // ==================== Scheduler Method Tests ====================

    @Test
    @DisplayName("Find due reminders returns only PENDING reminders")
    void findDueRemindersForProcessing_returnsDueReminders() {
        Instant now = Instant.now();
        List<Reminder> dueReminders = List.of(userAReminder);

        when(reminderRepository.findDueRemindersWithDetails(eq(ReminderStatus.PENDING), any(Instant.class)))
                .thenReturn(dueReminders);

        List<Reminder> result = reminderService.findDueRemindersForProcessing(now, 50);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("Find due reminders respects max batch size")
    void findDueRemindersForProcessing_respectsMaxBatchSize() {
        Instant now = Instant.now();
        List<Reminder> dueReminders = List.of(userAReminder, userAReminder, userAReminder);

        when(reminderRepository.findDueRemindersWithDetails(eq(ReminderStatus.PENDING), any(Instant.class)))
                .thenReturn(dueReminders);

        List<Reminder> result = reminderService.findDueRemindersForProcessing(now, 2);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("Mark reminder as sent updates status and timestamp")
    void markReminderAsSent_updatesStatusAndTimestamp() {
        Reminder reminder = Reminder.builder()
                .id(100L)
                .user(userA)
                .task(userATask)
                .scheduledAt(Instant.now())
                .status(ReminderStatus.PENDING)
                .retryCount(0)
                .build();

        when(reminderRepository.save(any(Reminder.class))).thenReturn(reminder);

        reminderService.markReminderAsSent(reminder);

        assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.SENT);
        assertThat(reminder.getSentAt()).isNotNull();
        verify(reminderRepository).save(reminder);
    }

    @Test
    @DisplayName("Mark reminder as failed increments retry count")
    void markReminderAsFailed_incrementsRetryCount() {
        Reminder reminder = Reminder.builder()
                .id(100L)
                .user(userA)
                .task(userATask)
                .scheduledAt(Instant.now())
                .status(ReminderStatus.PENDING)
                .retryCount(0)
                .build();

        when(reminderRepository.save(any(Reminder.class))).thenReturn(reminder);

        reminderService.markReminderAsFailed(reminder, "SMTP timeout");

        assertThat(reminder.getRetryCount()).isEqualTo(1);
        assertThat(reminder.getFailureReason()).isEqualTo("SMTP timeout");
        assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.PENDING);
        verify(reminderRepository).save(reminder);
    }

    @Test
    @DisplayName("Mark reminder as failed after max retries sets status to FAILED")
    void markReminderAsFailed_afterMaxRetries_setsFailedStatus() {
        Reminder reminder = Reminder.builder()
                .id(100L)
                .user(userA)
                .task(userATask)
                .scheduledAt(Instant.now())
                .status(ReminderStatus.PENDING)
                .retryCount(2)
                .build();

        when(reminderRepository.save(any(Reminder.class))).thenReturn(reminder);

        reminderService.markReminderAsFailed(reminder, "SMTP timeout");

        assertThat(reminder.getRetryCount()).isEqualTo(3);
        assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.FAILED);
        verify(reminderRepository).save(reminder);
    }

    @Test
    @DisplayName("List user reminders returns all reminders")
    void getUserReminders_returnsAllReminders() {
        setAuthenticatedUser("user-a@example.com");

        when(userRepository.findByEmailIgnoreCase("user-a@example.com")).thenReturn(Optional.of(userA));
        when(reminderRepository.findByUserIdWithTask(1L)).thenReturn(List.of(userAReminder));

        List<ReminderResponse> responses = reminderService.getUserReminders();

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).id()).isEqualTo(100L);
        clearAuthentication();
    }
}
