package com.smarttaskmanager.task.integration;

import com.smarttaskmanager.common.config.ApplicationProperties;
import com.smarttaskmanager.security.jwt.JwtService;
import com.smarttaskmanager.task.entity.RecurrenceType;
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
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.transaction.annotation.Transactional
class TaskRecurrenceIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ApplicationProperties applicationProperties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JwtService jwtService;

    private User userA;
    private User userB;
    private Task userATask;

    private String userAToken;
    private String userBToken;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(applicationProperties);

        userA = User.builder()
                .fullName("User A")
                .email("rec-a@example.com")
                .passwordHash(passwordEncoder.encode("TestPassword@123"))
                .role(Role.ROLE_USER)
                .enabled(true)
                .emailVerified(true)
                .build();
        userA = userRepository.save(userA);

        userB = User.builder()
                .fullName("User B")
                .email("rec-b@example.com")
                .passwordHash(passwordEncoder.encode("TestPassword@123"))
                .role(Role.ROLE_USER)
                .enabled(true)
                .emailVerified(true)
                .build();
        userB = userRepository.save(userB);

        Instant dueDate = Instant.now().plus(7, ChronoUnit.DAYS);
        userATask = Task.builder()
                .user(userA)
                .title("Recurring Task")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.HIGH)
                .dueDate(dueDate)
                .build();
        userATask = taskRepository.save(userATask);

        userAToken = jwtService.generateTokenWithExpiration(Map.of(), userA.getEmail(), applicationProperties.jwt().accessTokenExpirationMs());
        userBToken = jwtService.generateTokenWithExpiration(Map.of(), userB.getEmail(), applicationProperties.jwt().accessTokenExpirationMs());
    }

    // ==================== Unauthenticated Access Tests ====================

    @Test
    @DisplayName("Unauthenticated request to create recurrence is rejected")
    void createRecurrence_unauthenticated_returns401() throws Exception {
        String json = """
                {"recurrenceType": "DAILY"}
                """;
        mockMvc.perform(post("/api/v1/tasks/" + userATask.getId() + "/recurrence")
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isUnauthorized());
    }

    // ==================== Create Recurrence Tests ====================

    @Test
    @DisplayName("User can create a daily recurrence rule")
    void createRecurrence_daily_returns201() throws Exception {
        String json = """
                {
                    "recurrenceType": "DAILY",
                    "interval": 1
                }
                """;
        mockMvc.perform(post("/api/v1/tasks/" + userATask.getId() + "/recurrence")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.recurrenceType").value("DAILY"))
                .andExpect(jsonPath("$.data.interval").value(1))
                .andExpect(jsonPath("$.data.isPaused").value(false));
    }

    @Test
    @DisplayName("User can create a weekly recurrence rule")
    void createRecurrence_weekly_returns201() throws Exception {
        String json = """
                {
                    "recurrenceType": "WEEKLY",
                    "interval": 2
                }
                """;
        mockMvc.perform(post("/api/v1/tasks/" + userATask.getId() + "/recurrence")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.recurrenceType").value("WEEKLY"))
                .andExpect(jsonPath("$.data.interval").value(2));
    }

    @Test
    @DisplayName("User can create a monthly recurrence rule")
    void createRecurrence_monthly_returns201() throws Exception {
        String json = """
                {
                    "recurrenceType": "MONTHLY",
                    "interval": 1
                }
                """;
        mockMvc.perform(post("/api/v1/tasks/" + userATask.getId() + "/recurrence")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.recurrenceType").value("MONTHLY"));
    }

    @Test
    @DisplayName("Duplicate recurrence rule is rejected")
    void createRecurrence_duplicate_returns400() throws Exception {
        String json = """
                {"recurrenceType": "DAILY"}
                """;
        mockMvc.perform(post("/api/v1/tasks/" + userATask.getId() + "/recurrence")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/tasks/" + userATask.getId() + "/recurrence")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("Create recurrence with missing type is rejected")
    void createRecurrence_missingType_returns400() throws Exception {
        String json = """
                {"interval": 1}
                """;
        mockMvc.perform(post("/api/v1/tasks/" + userATask.getId() + "/recurrence")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("User A cannot create recurrence for User B's task")
    void createRecurrence_otherUserTask_returns404() throws Exception {
        Task userBTask = Task.builder()
                .user(userB)
                .title("User B Task")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.HIGH)
                .dueDate(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();
        userBTask = taskRepository.save(userBTask);

        String json = """
                {"recurrenceType": "DAILY"}
                """;
        mockMvc.perform(post("/api/v1/tasks/" + userBTask.getId() + "/recurrence")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // ==================== Get Recurrence Tests ====================

    @Test
    @DisplayName("User can get their recurrence rule")
    void getRecurrence_exists_returns200() throws Exception {
        String json = """
                {"recurrenceType": "WEEKLY"}
                """;
        mockMvc.perform(post("/api/v1/tasks/" + userATask.getId() + "/recurrence")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/tasks/" + userATask.getId() + "/recurrence")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recurrenceType").value("WEEKLY"));
    }

    @Test
    @DisplayName("Get non-existent recurrence returns 404")
    void getRecurrence_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/tasks/" + userATask.getId() + "/recurrence")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // ==================== Update Recurrence Tests ====================

    @Test
    @DisplayName("User can update their recurrence rule")
    void updateRecurrence_valid_returns200() throws Exception {
        String json = """
                {"recurrenceType": "DAILY"}
                """;
        mockMvc.perform(post("/api/v1/tasks/" + userATask.getId() + "/recurrence")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isCreated());

        String updateJson = """
                {
                    "recurrenceType": "WEEKLY",
                    "interval": 2
                }
                """;
        mockMvc.perform(put("/api/v1/tasks/" + userATask.getId() + "/recurrence")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recurrenceType").value("WEEKLY"))
                .andExpect(jsonPath("$.data.interval").value(2));
    }

    // ==================== Pause/Resume Recurrence Tests ====================

    @Test
    @DisplayName("User can pause their recurrence rule")
    void pauseRecurrence_valid_returns200() throws Exception {
        String json = """
                {"recurrenceType": "DAILY"}
                """;
        mockMvc.perform(post("/api/v1/tasks/" + userATask.getId() + "/recurrence")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/tasks/" + userATask.getId() + "/recurrence/pause")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isPaused").value(true));
    }

    @Test
    @DisplayName("User can resume their recurrence rule")
    void resumeRecurrence_valid_returns200() throws Exception {
        String json = """
                {"recurrenceType": "DAILY"}
                """;
        mockMvc.perform(post("/api/v1/tasks/" + userATask.getId() + "/recurrence")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/tasks/" + userATask.getId() + "/recurrence/pause")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isPaused").value(true));

        mockMvc.perform(post("/api/v1/tasks/" + userATask.getId() + "/recurrence/resume")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isPaused").value(false));
    }

    // ==================== Delete Recurrence Tests ====================

    @Test
    @DisplayName("User can delete their recurrence rule")
    void deleteRecurrence_valid_returns200() throws Exception {
        String json = """
                {"recurrenceType": "DAILY"}
                """;
        mockMvc.perform(post("/api/v1/tasks/" + userATask.getId() + "/recurrence")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/tasks/" + userATask.getId() + "/recurrence")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/v1/tasks/" + userATask.getId() + "/recurrence")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isNotFound());
    }

    // ==================== Next Occurrence Calculation Tests ====================

    @Test
    @DisplayName("Daily recurrence calculates next occurrence correctly")
    void createRecurrence_dailyNextOccurrence_isCorrect() throws Exception {
        String json = """
                {"recurrenceType": "DAILY", "interval": 1}
                """;
        MvcResult result = mockMvc.perform(post("/api/v1/tasks/" + userATask.getId() + "/recurrence")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String nextOccurrenceStr = objectMapper.readTree(body).path("data").path("nextOccurrenceAt").asText();
        Instant nextOccurrence = Instant.parse(nextOccurrenceStr);

        // Next occurrence should be after the task's due date
        assertThat(nextOccurrence).isAfter(userATask.getDueDate());
    }

    @Test
    @DisplayName("Custom weekly recurrence with specific days")
    void createRecurrence_customWeekly_returns201() throws Exception {
        String json = """
                {
                    "recurrenceType": "CUSTOM_WEEKLY",
                    "weekDays": "MONDAY,WEDNESDAY,FRIDAY"
                }
                """;
        mockMvc.perform(post("/api/v1/tasks/" + userATask.getId() + "/recurrence")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.recurrenceType").value("CUSTOM_WEEKLY"))
                .andExpect(jsonPath("$.data.weekDays").value("MONDAY,WEDNESDAY,FRIDAY"));
    }

    // ==================== Invalid JWT Tests ====================

    @Test
    @DisplayName("Invalid JWT cannot access recurrence endpoints")
    void createRecurrence_invalidJwt_returns401() throws Exception {
        String json = """
                {"recurrenceType": "DAILY"}
                """;
        mockMvc.perform(post("/api/v1/tasks/" + userATask.getId() + "/recurrence")
                        .header("Authorization", "Bearer invalid.token.here")
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isUnauthorized());
    }
}
