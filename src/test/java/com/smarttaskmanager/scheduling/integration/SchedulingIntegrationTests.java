package com.smarttaskmanager.scheduling.integration;

import com.smarttaskmanager.common.config.ApplicationProperties;
import com.smarttaskmanager.security.jwt.JwtService;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the Smart Scheduling Engine endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SchedulingIntegrationTests {

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

    private JwtService jwtService;
    private User testUser;
    private String authToken;
    private Long taskId;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(applicationProperties);

        // Create a verified user directly in the database
        testUser = User.builder()
                .fullName("Scheduling User")
                .email("scheduling-test@example.com")
                .passwordHash(passwordEncoder.encode("TestPassword@123"))
                .role(Role.ROLE_USER)
                .enabled(true)
                .emailVerified(true)
                .build();
        testUser = userRepository.save(testUser);

        // Generate JWT token
        authToken = jwtService.generateTokenWithExpiration(
                Map.of(), testUser.getEmail(), applicationProperties.jwt().accessTokenExpirationMs());

        // Create a task for scheduling
        Instant dueDate = Instant.now().plus(7, ChronoUnit.DAYS);
        Task task = Task.builder()
                .user(testUser)
                .title("Scheduling Test Task")
                .description("Test task for scheduling")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.HIGH)
                .dueDate(dueDate)
                .estimatedDurationMinutes(60)
                .timezone("UTC")
                .build();
        task = taskRepository.save(task);
        taskId = task.getId();
    }

    @Test
    @DisplayName("GET /scheduling/preferences returns default preferences")
    void getPreferences_returnsDefaults() throws Exception {
        mockMvc.perform(get("/api/v1/scheduling/preferences")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.timezone").value("UTC"))
                .andExpect(jsonPath("$.data.workDayStartTime").value("09:00"))
                .andExpect(jsonPath("$.data.workDayEndTime").value("17:00"))
                .andExpect(jsonPath("$.data.defaultTaskDurationMinutes").value(60));
    }

    @Test
    @DisplayName("PUT /scheduling/preferences updates preferences")
    void updatePreferences_success() throws Exception {
        mockMvc.perform(put("/api/v1/scheduling/preferences")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "timezone": "Asia/Kolkata",
                                    "workDayStartTime": "08:00",
                                    "workDayEndTime": "18:00",
                                    "workDaysOfWeek": ["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY"],
                                    "defaultTaskDurationMinutes": 45,
                                    "breakDurationMinutes": 10
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.timezone").value("ASIA/KOLKATA"))
                .andExpect(jsonPath("$.data.workDayStartTime").value("08:00"))
                .andExpect(jsonPath("$.data.defaultTaskDurationMinutes").value(45));
    }

    @Test
    @DisplayName("PUT /scheduling/preferences rejects invalid timezone")
    void updatePreferences_invalidTimezone_returns400() throws Exception {
        mockMvc.perform(put("/api/v1/scheduling/preferences")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "timezone": "Invalid/Timezone",
                                    "workDayStartTime": "09:00",
                                    "workDayEndTime": "17:00"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /scheduling/preferences rejects end time before start time")
    void updatePreferences_endBeforeStart_returns400() throws Exception {
        mockMvc.perform(put("/api/v1/scheduling/preferences")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "timezone": "UTC",
                                    "workDayStartTime": "17:00",
                                    "workDayEndTime": "09:00"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /scheduling/plan generates a schedule")
    void generatePlan_returnsSchedule() throws Exception {
        mockMvc.perform(post("/api/v1/scheduling/plan")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.scheduledTasks").isNotEmpty())
                .andExpect(jsonPath("$.data.planGeneratedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.summary").isNotEmpty());
    }

    @Test
    @DisplayName("GET /scheduling/plan returns current plan")
    void getCurrentPlan_returnsPlan() throws Exception {
        mockMvc.perform(get("/api/v1/scheduling/plan")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /scheduling/plan/apply applies the schedule")
    void applyPlan_success() throws Exception {
        mockMvc.perform(post("/api/v1/scheduling/plan/apply")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.scheduledTasks").isNotEmpty());
    }

    @Test
    @DisplayName("GET /scheduling/conflicts returns conflicts")
    void getConflicts_returnsEmpty() throws Exception {
        mockMvc.perform(get("/api/v1/scheduling/conflicts")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("GET /scheduling/deadline-risks returns risks")
    void getDeadlineRisks_returnsRisks() throws Exception {
        mockMvc.perform(get("/api/v1/scheduling/deadline-risks")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("Unauthenticated requests are rejected")
    void unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/scheduling/preferences"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/scheduling/plan"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/v1/scheduling/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Invalid JWT returns 401")
    void invalidJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/scheduling/preferences")
                        .header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /scheduling/preferences validates required fields")
    void updatePreferences_missingFields_returns400() throws Exception {
        mockMvc.perform(put("/api/v1/scheduling/preferences")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

}
