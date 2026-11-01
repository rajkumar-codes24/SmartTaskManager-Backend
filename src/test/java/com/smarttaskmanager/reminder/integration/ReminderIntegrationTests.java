package com.smarttaskmanager.reminder.integration;

import com.smarttaskmanager.common.config.ApplicationProperties;
import com.smarttaskmanager.reminder.entity.Reminder;
import com.smarttaskmanager.reminder.entity.ReminderStatus;
import com.smarttaskmanager.reminder.repository.ReminderRepository;
import com.smarttaskmanager.security.jwt.JwtService;
import com.smarttaskmanager.task.entity.Task;
import com.smarttaskmanager.task.entity.TaskPriority;
import com.smarttaskmanager.task.entity.TaskStatus;
import com.smarttaskmanager.task.repository.TaskRepository;
import com.smarttaskmanager.user.entity.Role;
import com.smarttaskmanager.user.entity.User;
import com.smarttaskmanager.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.transaction.annotation.Transactional
class ReminderIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ReminderRepository reminderRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ApplicationProperties applicationProperties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JwtService jwtService;

    private User userA;
    private User userB;
    private Task userATask;
    private Task userBTask;
    private Reminder userAReminder;

    private String userAToken;
    private String userBToken;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(applicationProperties);

        // Create two users
        userA = User.builder()
                .fullName("User A")
                .email("reminder-a@example.com")
                .passwordHash(passwordEncoder.encode("TestPassword@123"))
                .role(Role.ROLE_USER)
                .enabled(true)
                .emailVerified(true)
                .build();
        userA = userRepository.save(userA);

        userB = User.builder()
                .fullName("User B")
                .email("reminder-b@example.com")
                .passwordHash(passwordEncoder.encode("TestPassword@123"))
                .role(Role.ROLE_USER)
                .enabled(true)
                .emailVerified(true)
                .build();
        userB = userRepository.save(userB);

        // Create tasks
        userATask = Task.builder()
                .user(userA)
                .title("Complete project")
                .description("Finish the SmartTaskManager project")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.HIGH)
                .dueDate(Instant.parse("2027-12-31T23:59:59Z"))
                .build();
        userATask = taskRepository.save(userATask);

        userBTask = Task.builder()
                .user(userB)
                .title("User B's task")
                .description("Personal task")
                .status(TaskStatus.IN_PROGRESS)
                .priority(TaskPriority.MEDIUM)
                .build();
        userBTask = taskRepository.save(userBTask);

        // Create a reminder for userA
        userAReminder = Reminder.builder()
                .user(userA)
                .task(userATask)
                .scheduledAt(Instant.now().plusSeconds(3600))
                .status(ReminderStatus.PENDING)
                .retryCount(0)
                .build();
        userAReminder = reminderRepository.save(userAReminder);

        // Generate JWT tokens
        userAToken = jwtService.generateTokenWithExpiration(
                Map.of(), userA.getEmail(), applicationProperties.jwt().accessTokenExpirationMs());
        userBToken = jwtService.generateTokenWithExpiration(
                Map.of(), userB.getEmail(), applicationProperties.jwt().accessTokenExpirationMs());
    }

    // ==================== Unauthenticated Access Tests ====================

    @Test
    @DisplayName("Unauthenticated request to create reminder is rejected")
    void createReminder_unauthenticated_returns401() throws Exception {
        String createJson = """
                {
                    "taskId": 1,
                    "scheduledAt": "2027-12-31T23:59:59Z"
                }
                """;

        mockMvc.perform(post("/api/v1/reminders")
                        .contentType("application/json")
                        .content(createJson))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Unauthenticated request to list reminders is rejected")
    void listReminders_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/reminders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Unauthenticated request to get reminder by ID is rejected")
    void getReminderById_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/reminders/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Unauthenticated request to update reminder is rejected")
    void updateReminder_unauthenticated_returns401() throws Exception {
        String updateJson = """
                {
                    "scheduledAt": "2027-12-31T23:59:59Z"
                }
                """;

        mockMvc.perform(put("/api/v1/reminders/1")
                        .contentType("application/json")
                        .content(updateJson))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Unauthenticated request to delete reminder is rejected")
    void deleteReminder_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/api/v1/reminders/1"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== Create Reminder Tests ====================

    @Test
    @DisplayName("Authenticated user can create a reminder")
    void createReminder_authenticated_returns201() throws Exception {
        Instant futureTime = Instant.now().plusSeconds(7200);
        String createJson = """
                {
                    "taskId": %d,
                    "scheduledAt": "%s"
                }
                """.formatted(userATask.getId(), futureTime.toString());

        mockMvc.perform(post("/api/v1/reminders")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(createJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.taskId").value(userATask.getId()))
                .andExpect(jsonPath("$.data.taskTitle").value("Complete project"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.retryCount").value(0));
    }

    @Test
    @DisplayName("Create reminder for non-existent task returns 404")
    void createReminder_nonExistentTask_returns404() throws Exception {
        Instant futureTime = Instant.now().plusSeconds(7200);
        String createJson = """
                {
                    "taskId": 999,
                    "scheduledAt": "%s"
                }
                """.formatted(futureTime.toString());

        mockMvc.perform(post("/api/v1/reminders")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(createJson))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("Create reminder for another user's task returns 404")
    void createReminder_anotherUserTask_returns404() throws Exception {
        Instant futureTime = Instant.now().plusSeconds(7200);
        String createJson = """
                {
                    "taskId": %d,
                    "scheduledAt": "%s"
                }
                """.formatted(userBTask.getId(), futureTime.toString());

        mockMvc.perform(post("/api/v1/reminders")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(createJson))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("Create reminder with past date returns 400")
    void createReminder_pastDate_returns400() throws Exception {
        Instant pastTime = Instant.now().minusSeconds(3600);
        String createJson = """
                {
                    "taskId": %d,
                    "scheduledAt": "%s"
                }
                """.formatted(userATask.getId(), pastTime.toString());

        mockMvc.perform(post("/api/v1/reminders")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(createJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("Create reminder with missing taskId returns 400")
    void createReminder_missingTaskId_returns400() throws Exception {
        Instant futureTime = Instant.now().plusSeconds(7200);
        String createJson = """
                {
                    "scheduledAt": "%s"
                }
                """.formatted(futureTime.toString());

        mockMvc.perform(post("/api/v1/reminders")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(createJson))
                .andExpect(status().isBadRequest());
    }

    // ==================== List Reminders Tests ====================

    @Test
    @DisplayName("Authenticated user can list their reminders")
    void listReminders_authenticated_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/reminders")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].taskId").value(userATask.getId()));
    }

    @Test
    @DisplayName("User B cannot see User A's reminders")
    void listReminders_userB_cannotSeeUserAReminders() throws Exception {
        mockMvc.perform(get("/api/v1/reminders")
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // ==================== Get Reminder by ID Tests ====================

    @Test
    @DisplayName("Authenticated user can get their reminder by ID")
    void getReminderById_authenticated_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/reminders/" + userAReminder.getId())
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(userAReminder.getId()))
                .andExpect(jsonPath("$.data.taskTitle").value("Complete project"));
    }

    @Test
    @DisplayName("User B cannot access User A's reminder")
    void getReminderById_userB_cannotAccessUserAReminder() throws Exception {
        mockMvc.perform(get("/api/v1/reminders/" + userAReminder.getId())
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("Get non-existent reminder returns 404")
    void getReminderById_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/reminders/999")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // ==================== Update Reminder Tests ====================

    @Test
    @DisplayName("Authenticated user can update their reminder")
    void updateReminder_authenticated_returns200() throws Exception {
        Instant newTime = Instant.now().plusSeconds(14400);
        String updateJson = """
                {
                    "scheduledAt": "%s"
                }
                """.formatted(newTime.toString());

        mockMvc.perform(put("/api/v1/reminders/" + userAReminder.getId())
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("User B cannot update User A's reminder")
    void updateReminder_userB_cannotUpdateUserAReminder() throws Exception {
        Instant newTime = Instant.now().plusSeconds(14400);
        String updateJson = """
                {
                    "scheduledAt": "%s"
                }
                """.formatted(newTime.toString());

        mockMvc.perform(put("/api/v1/reminders/" + userAReminder.getId())
                        .header("Authorization", "Bearer " + userBToken)
                        .contentType("application/json")
                        .content(updateJson))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    // ==================== Delete Reminder Tests ====================

    @Test
    @DisplayName("Authenticated user can delete their reminder")
    void deleteReminder_authenticated_returns200() throws Exception {
        // Create a new reminder to delete (not the one used in other tests)
        Instant futureTime = Instant.now().plusSeconds(7200);
        Reminder toDelete = Reminder.builder()
                .user(userA)
                .task(userATask)
                .scheduledAt(futureTime)
                .status(ReminderStatus.PENDING)
                .retryCount(0)
                .build();
        toDelete = reminderRepository.save(toDelete);

        mockMvc.perform(delete("/api/v1/reminders/" + toDelete.getId())
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(reminderRepository.findById(toDelete.getId())).isEmpty();
    }

    @Test
    @DisplayName("User B cannot delete User A's reminder")
    void deleteReminder_userB_cannotDeleteUserAReminder() throws Exception {
        mockMvc.perform(delete("/api/v1/reminders/" + userAReminder.getId())
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    // ==================== Invalid JWT Tests ====================

    @Test
    @DisplayName("Invalid JWT returns 401")
    void createReminder_invalidJwt_returns401() throws Exception {
        String createJson = """
                {
                    "taskId": 1,
                    "scheduledAt": "2027-12-31T23:59:59Z"
                }
                """;

        mockMvc.perform(post("/api/v1/reminders")
                        .header("Authorization", "Bearer invalid.token.here")
                        .contentType("application/json")
                        .content(createJson))
                .andExpect(status().isUnauthorized());
    }

    // ==================== Malformed JSON Tests ====================

    @Test
    @DisplayName("Malformed JSON returns 400")
    void createReminder_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/reminders")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content("{ invalid json }"))
                .andExpect(status().isBadRequest());
    }

    // ==================== Persistence Tests ====================

    @Test
    @DisplayName("Created reminder is persisted in database")
    void createReminder_persistsInDatabase() throws Exception {
        Instant futureTime = Instant.now().plusSeconds(7200);
        String createJson = """
                {
                    "taskId": %d,
                    "scheduledAt": "%s"
                }
                """.formatted(userATask.getId(), futureTime.toString());

        mockMvc.perform(post("/api/v1/reminders")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(createJson))
                .andExpect(status().isCreated());

        // Verify persistence
        long count = reminderRepository.findByUserId(userA.getId()).size();
        assertThat(count).isGreaterThanOrEqualTo(2); // original + new
    }
}
