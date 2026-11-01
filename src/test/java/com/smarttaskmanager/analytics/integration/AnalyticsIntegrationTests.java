package com.smarttaskmanager.analytics.integration;

import com.smarttaskmanager.analytics.entity.ProductivityGoal;
import com.smarttaskmanager.analytics.repository.ProductivityGoalRepository;
import com.smarttaskmanager.common.config.ApplicationProperties;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.transaction.annotation.Transactional
class AnalyticsIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ProductivityGoalRepository goalRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ApplicationProperties applicationProperties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JwtService jwtService;

    private User userA;
    private User userB;
    private String userAToken;
    private String userBToken;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(applicationProperties);

        userA = User.builder()
                .fullName("User A")
                .email("analytics-a@example.com")
                .passwordHash(passwordEncoder.encode("TestPassword@123"))
                .role(Role.ROLE_USER)
                .enabled(true)
                .emailVerified(true)
                .build();
        userA = userRepository.save(userA);

        userB = User.builder()
                .fullName("User B")
                .email("analytics-b@example.com")
                .passwordHash(passwordEncoder.encode("TestPassword@123"))
                .role(Role.ROLE_USER)
                .enabled(true)
                .emailVerified(true)
                .build();
        userB = userRepository.save(userB);

        userAToken = jwtService.generateTokenWithExpiration(
                Map.of(), userA.getEmail(), applicationProperties.jwt().accessTokenExpirationMs());
        userBToken = jwtService.generateTokenWithExpiration(
                Map.of(), userB.getEmail(), applicationProperties.jwt().accessTokenExpirationMs());
    }

    // ==================== AUTHENTICATION TESTS ====================

    @Test
    @DisplayName("Overview returns 401 without authentication")
    void overview_returns401_withoutAuth() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/overview"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Overview returns 401 with invalid JWT")
    void overview_returns401_withInvalidJwt() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/overview")
                        .header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== DASHBOARD OVERVIEW TESTS ====================

    @Test
    @DisplayName("Overview returns zero counts for empty dataset")
    void overview_returnsZeroCounts_forEmptyDataset() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/overview")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalTasks").value(0))
                .andExpect(jsonPath("$.data.completedTasks").value(0))
                .andExpect(jsonPath("$.data.pendingTasks").value(0))
                .andExpect(jsonPath("$.data.inProgressTasks").value(0))
                .andExpect(jsonPath("$.data.overdueTasks").value(0))
                .andExpect(jsonPath("$.data.completionPercentage").value(0.0));
    }

    @Test
    @DisplayName("Overview returns correct counts for mixed tasks")
    void overview_returnsCorrectCounts_forMixedTasks() throws Exception {
        // Create tasks with different statuses
        Task todo = Task.builder().user(userA).title("TODO Task").status(TaskStatus.TODO)
                .priority(TaskPriority.HIGH).build();
        Task inProgress = Task.builder().user(userA).title("In Progress Task")
                .status(TaskStatus.IN_PROGRESS).priority(TaskPriority.MEDIUM).build();
        Task completed = Task.builder().user(userA).title("Completed Task")
                .status(TaskStatus.COMPLETED).priority(TaskPriority.LOW)
                .completedAt(Instant.now()).build();
        Task overdue = Task.builder().user(userA).title("Overdue Task")
                .status(TaskStatus.TODO).priority(TaskPriority.URGENT)
                .dueDate(Instant.now().minus(2, ChronoUnit.DAYS)).build();

        taskRepository.saveAll(java.util.List.of(todo, inProgress, completed, overdue));

        mockMvc.perform(get("/api/v1/analytics/overview")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalTasks").value(4))
                .andExpect(jsonPath("$.data.completedTasks").value(1))
                .andExpect(jsonPath("$.data.inProgressTasks").value(1))
                .andExpect(jsonPath("$.data.overdueTasks").value(1));
    }

    @Test
    @DisplayName("Overview is isolated per user")
    void overview_isolatedPerUser() throws Exception {
        // Create tasks for both users
        taskRepository.save(Task.builder().user(userA).title("User A Task")
                .status(TaskStatus.COMPLETED).completedAt(Instant.now()).build());
        taskRepository.save(Task.builder().user(userB).title("User B Task")
                .status(TaskStatus.COMPLETED).completedAt(Instant.now()).build());

        mockMvc.perform(get("/api/v1/analytics/overview")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(jsonPath("$.data.totalTasks").value(1))
                .andExpect(jsonPath("$.data.completedTasks").value(1));

        mockMvc.perform(get("/api/v1/analytics/overview")
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(jsonPath("$.data.totalTasks").value(1))
                .andExpect(jsonPath("$.data.completedTasks").value(1));
    }

    // ==================== DAILY STATS TESTS ====================

    @Test
    @DisplayName("Daily stats returns empty list for no data")
    void dailyStats_returnsEmpty_forNoData() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/daily")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    // ==================== WEEKLY STATS TESTS ====================

    @Test
    @DisplayName("Weekly stats returns empty list for no data")
    void weeklyStats_returnsEmpty_forNoData() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/weekly")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    // ==================== MONTHLY STATS TESTS ====================

    @Test
    @DisplayName("Monthly stats returns empty list for no data")
    void monthlyStats_returnsEmpty_forNoData() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/monthly")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    // ==================== TRENDS TESTS ====================

    @Test
    @DisplayName("Trends returns valid response for empty dataset")
    void trends_returnsValidResponse_forEmptyDataset() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/trends")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.averageCompletionTimeMinutes").value(0.0))
                .andExpect(jsonPath("$.data.completionRate").value(0.0));
    }

    // ==================== DISTRIBUTION TESTS ====================

    @Test
    @DisplayName("Distribution returns zero counts for empty dataset")
    void distribution_returnsZeroCounts_forEmptyDataset() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/distribution")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.byPriority").isMap())
                .andExpect(jsonPath("$.data.byCategory").isMap());
    }

    // ==================== INSIGHTS TESTS ====================

    @Test
    @DisplayName("Insights returns message for empty dataset")
    void insights_returnsMessage_forEmptyDataset() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/insights")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.insights").isArray())
                .andExpect(jsonPath("$.data.insights[0].message").value(
                        "You have no tasks yet. Create your first task to start tracking productivity."));
    }

    @Test
    @DisplayName("Insights detects overdue tasks")
    void insights_detectsOverdueTasks() throws Exception {
        taskRepository.save(Task.builder().user(userA).title("Overdue Task")
                .status(TaskStatus.TODO).dueDate(Instant.now().minus(1, ChronoUnit.DAYS)).build());

        mockMvc.perform(get("/api/v1/analytics/insights")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.insights[0].message").isNotEmpty());
    }

    // ==================== GOAL CRUD TESTS ====================

    @Test
    @DisplayName("Create goal returns 200")
    void createGoal_returns200() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "goalType", "WEEKLY",
                "targetCount", 10,
                "periodStartDate", LocalDate.now().toString(),
                "periodEndDate", LocalDate.now().plusWeeks(1).toString()
        ));

        mockMvc.perform(post("/api/v1/analytics/goals")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.goalType").value("WEEKLY"))
                .andExpect(jsonPath("$.data.targetCount").value(10))
                .andExpect(jsonPath("$.data.isActive").value(true));
    }

    @Test
    @DisplayName("Create goal validates required fields")
    void createGoal_validatesRequiredFields() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "goalType", "WEEKLY",
                "targetCount", 10
                // Missing periodStartDate and periodEndDate
        ));

        mockMvc.perform(post("/api/v1/analytics/goals")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Create goal rejects invalid goal type")
    void createGoal_rejectsInvalidGoalType() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "goalType", "INVALID",
                "targetCount", 10,
                "periodStartDate", LocalDate.now().toString(),
                "periodEndDate", LocalDate.now().plusWeeks(1).toString()
        ));

        mockMvc.perform(post("/api/v1/analytics/goals")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("Create goal rejects end date before start date")
    void createGoal_rejectsEndDateBeforeStartDate() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "goalType", "DAILY",
                "targetCount", 5,
                "periodStartDate", LocalDate.now().plusDays(5).toString(),
                "periodEndDate", LocalDate.now().toString()
        ));

        mockMvc.perform(post("/api/v1/analytics/goals")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("List goals returns empty list initially")
    void listGoals_returnsEmptyListInitially() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/goals")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("List goals returns created goals")
    void listGoals_returnsCreatedGoals() throws Exception {
        // Create a goal
        String body = objectMapper.writeValueAsString(Map.of(
                "goalType", "MONTHLY",
                "targetCount", 30,
                "periodStartDate", LocalDate.now().toString(),
                "periodEndDate", LocalDate.now().plusMonths(1).toString()
        ));
        mockMvc.perform(post("/api/v1/analytics/goals")
                .header("Authorization", "Bearer " + userAToken)
                .contentType("application/json")
                .content(body));

        mockMvc.perform(get("/api/v1/analytics/goals")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].goalType").value("MONTHLY"));
    }

    @Test
    @DisplayName("Goals are isolated per user")
    void goals_areIsolatedPerUser() throws Exception {
        // Create goals for both users
        String bodyA = objectMapper.writeValueAsString(Map.of(
                "goalType", "DAILY",
                "targetCount", 5,
                "periodStartDate", LocalDate.now().toString(),
                "periodEndDate", LocalDate.now().plusDays(1).toString()
        ));
        mockMvc.perform(post("/api/v1/analytics/goals")
                .header("Authorization", "Bearer " + userAToken)
                .contentType("application/json")
                .content(bodyA));

        String bodyB = objectMapper.writeValueAsString(Map.of(
                "goalType", "WEEKLY",
                "targetCount", 20,
                "periodStartDate", LocalDate.now().toString(),
                "periodEndDate", LocalDate.now().plusWeeks(1).toString()
        ));
        mockMvc.perform(post("/api/v1/analytics/goals")
                .header("Authorization", "Bearer " + userBToken)
                .contentType("application/json")
                .content(bodyB));

        mockMvc.perform(get("/api/v1/analytics/goals")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].goalType").value("DAILY"));

        mockMvc.perform(get("/api/v1/analytics/goals")
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].goalType").value("WEEKLY"));
    }

    @Test
    @DisplayName("Update goal works correctly")
    void updateGoal_worksCorrectly() throws Exception {
        // Create a goal
        String body = objectMapper.writeValueAsString(Map.of(
                "goalType", "DAILY",
                "targetCount", 5,
                "periodStartDate", LocalDate.now().toString(),
                "periodEndDate", LocalDate.now().plusDays(1).toString()
        ));
        String response = mockMvc.perform(post("/api/v1/analytics/goals")
                .header("Authorization", "Bearer " + userAToken)
                .contentType("application/json")
                .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Long goalId = objectMapper.readTree(response).path("data").path("id").asLong();

        // Update the goal
        String updateBody = objectMapper.writeValueAsString(Map.of(
                "targetCount", 10
        ));
        mockMvc.perform(put("/api/v1/analytics/goals/" + goalId)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetCount").value(10));
    }

    @Test
    @DisplayName("Update goal rejects cross-user access")
    void updateGoal_rejectsCrossUserAccess() throws Exception {
        // Create a goal for user A
        String body = objectMapper.writeValueAsString(Map.of(
                "goalType", "DAILY",
                "targetCount", 5,
                "periodStartDate", LocalDate.now().toString(),
                "periodEndDate", LocalDate.now().plusDays(1).toString()
        ));
        String response = mockMvc.perform(post("/api/v1/analytics/goals")
                .header("Authorization", "Bearer " + userAToken)
                .contentType("application/json")
                .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Long goalId = objectMapper.readTree(response).path("data").path("id").asLong();

        // User B tries to update
        String updateBody = objectMapper.writeValueAsString(Map.of(
                "targetCount", 100
        ));
        mockMvc.perform(put("/api/v1/analytics/goals/" + goalId)
                        .header("Authorization", "Bearer " + userBToken)
                        .contentType("application/json")
                        .content(updateBody))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Delete goal works correctly")
    void deleteGoal_worksCorrectly() throws Exception {
        // Create a goal
        String body = objectMapper.writeValueAsString(Map.of(
                "goalType", "WEEKLY",
                "targetCount", 10,
                "periodStartDate", LocalDate.now().toString(),
                "periodEndDate", LocalDate.now().plusWeeks(1).toString()
        ));
        String response = mockMvc.perform(post("/api/v1/analytics/goals")
                .header("Authorization", "Bearer " + userAToken)
                .contentType("application/json")
                .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Long goalId = objectMapper.readTree(response).path("data").path("id").asLong();

        // Delete the goal
        mockMvc.perform(delete("/api/v1/analytics/goals/" + goalId)
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Verify it's gone
        mockMvc.perform(get("/api/v1/analytics/goals")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("Delete goal rejects cross-user access")
    void deleteGoal_rejectsCrossUserAccess() throws Exception {
        // Create a goal for user A
        String body = objectMapper.writeValueAsString(Map.of(
                "goalType", "DAILY",
                "targetCount", 5,
                "periodStartDate", LocalDate.now().toString(),
                "periodEndDate", LocalDate.now().plusDays(1).toString()
        ));
        String response = mockMvc.perform(post("/api/v1/analytics/goals")
                .header("Authorization", "Bearer " + userAToken)
                .contentType("application/json")
                .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Long goalId = objectMapper.readTree(response).path("data").path("id").asLong();

        // User B tries to delete
        mockMvc.perform(delete("/api/v1/analytics/goals/" + goalId)
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Goal progress percentage is calculated correctly")
    void goalProgressPercentage_calculatedCorrectly() throws Exception {
        // Create a goal with target 10
        String body = objectMapper.writeValueAsString(Map.of(
                "goalType", "DAILY",
                "targetCount", 10,
                "periodStartDate", LocalDate.now().toString(),
                "periodEndDate", LocalDate.now().plusDays(1).toString()
        ));
        mockMvc.perform(post("/api/v1/analytics/goals")
                .header("Authorization", "Bearer " + userAToken)
                .contentType("application/json")
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.progressPercentage").value(0.0));
    }
}
