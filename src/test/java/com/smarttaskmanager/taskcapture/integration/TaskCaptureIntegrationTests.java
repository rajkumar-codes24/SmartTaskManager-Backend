package com.smarttaskmanager.taskcapture.integration;

import com.smarttaskmanager.common.config.ApplicationProperties;
import com.smarttaskmanager.security.jwt.JwtService;
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

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.transaction.annotation.Transactional
class TaskCaptureIntegrationTests {

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
    private User user;
    private String token;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(applicationProperties);

        user = User.builder()
                .fullName("Test User")
                .email("taskcapture@example.com")
                .passwordHash(passwordEncoder.encode("TestPassword@123"))
                .emailVerified(true)
                .role(Role.ROLE_USER)
                .enabled(true)
                .build();
        user = userRepository.save(user);
        org.springframework.security.core.userdetails.User userDetails =
                new org.springframework.security.core.userdetails.User(
                        user.getEmail(), user.getPasswordHash(),
                        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(user.getRole().name()))
                );
        token = jwtService.generateAccessToken(userDetails);
    }

    @Test
    @DisplayName("Parse returns 200 with valid input")
    void parse_returns200_withValidInput() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("text", "Prepare for Java interview tomorrow at 7 PM, high priority"));

        mockMvc.perform(post("/api/v1/task-capture/parse")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").isNotEmpty())
                .andExpect(jsonPath("$.data.priority").value("HIGH"))
                .andExpect(jsonPath("$.data.provider").value("rulebased"));
    }

    @Test
    @DisplayName("Parse extracts due date for tomorrow")
    void parse_extractsDueDate_forTomorrow() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("text", "Call doctor tomorrow at 10:00"));

        mockMvc.perform(post("/api/v1/task-capture/parse")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dueDate").isNotEmpty());
    }

    @Test
    @DisplayName("Parse extracts priority")
    void parse_extractsPriority() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("text", "Fix critical bug, urgent priority"));

        mockMvc.perform(post("/api/v1/task-capture/parse")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.priority").value("URGENT"));
    }

    @Test
    @DisplayName("Parse extracts recurrence")
    void parse_extractsRecurrence() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("text", "Exercise every day"));

        mockMvc.perform(post("/api/v1/task-capture/parse")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recurrenceType").value("DAILY"));
    }

    @Test
    @DisplayName("Parse extracts duration")
    void parse_extractsDuration() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("text", "Write report 30 minutes"));

        mockMvc.perform(post("/api/v1/task-capture/parse")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.estimatedDurationMinutes").value(30));
    }

    @Test
    @DisplayName("Parse rejects empty text")
    void parse_rejectsEmptyText() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("text", ""));

        mockMvc.perform(post("/api/v1/task-capture/parse")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Parse rejects missing text")
    void parse_rejectsMissingText() throws Exception {
        String body = "{}";

        mockMvc.perform(post("/api/v1/task-capture/parse")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Parse returns 401 without auth")
    void parse_returns401_withoutAuth() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("text", "Test task"));

        mockMvc.perform(post("/api/v1/task-capture/parse")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Confirm creates task successfully")
    void confirm_createsTaskSuccessfully() throws Exception {
        long taskCountBefore = taskRepository.count();

        String body = objectMapper.writeValueAsString(Map.of(
                "title", "Test Task",
                "priority", "HIGH",
                "status", "TODO"
        ));

        mockMvc.perform(post("/api/v1/task-capture/confirm")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Test Task"));

        long taskCountAfter = taskRepository.count();
        // Task count should have increased by 1
        assert taskCountAfter == taskCountBefore + 1 : "Task should have been created";
    }

    @Test
    @DisplayName("Confirm creates task with category")
    void confirm_createsTaskWithCategory() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "title", "Work Task",
                "categoryName", "Work",
                "priority", "MEDIUM"
        ));

        mockMvc.perform(post("/api/v1/task-capture/confirm")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("Confirm rejects empty title")
    void confirm_rejectsEmptyTitle() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("title", ""));

        mockMvc.perform(post("/api/v1/task-capture/confirm")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Confirm returns 401 without auth")
    void confirm_returns401_withoutAuth() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("title", "Test Task"));

        mockMvc.perform(post("/api/v1/task-capture/confirm")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Confirm with invalid JWT returns 401")
    void confirm_invalidJwt_returns401() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("title", "Test Task"));

        mockMvc.perform(post("/api/v1/task-capture/confirm")
                        .header("Authorization", "Bearer invalid.token.here")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Parse handles next Monday")
    void parse_handlesNextMonday() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("text", "Submit report next Monday"));

        mockMvc.perform(post("/api/v1/task-capture/parse")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dueDate").isNotEmpty());
    }

    @Test
    @DisplayName("Parse handles in N days")
    void parse_handlesInNDays() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("text", "Pay bill in 3 days"));

        mockMvc.perform(post("/api/v1/task-capture/parse")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dueDate").isNotEmpty());
    }

    @Test
    @DisplayName("Parse handles every N days recurrence")
    void parse_handlesEveryNDays() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("text", "Take medication every 2 days"));

        mockMvc.perform(post("/api/v1/task-capture/parse")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recurrenceType").value("EVERY_N_DAYS"))
                .andExpect(jsonPath("$.data.recurrenceInterval").value(2));
    }

    @Test
    @DisplayName("Parse handles low priority")
    void parse_handlesLowPriority() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("text", "Clean desk low priority"));

        mockMvc.perform(post("/api/v1/task-capture/parse")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.priority").value("LOW"));
    }

    @Test
    @DisplayName("Parse handles category extraction")
    void parse_handlesCategoryExtraction() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("text", "Go to the gym for health"));

        mockMvc.perform(post("/api/v1/task-capture/parse")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categoryName").value("Health"));
    }
}
