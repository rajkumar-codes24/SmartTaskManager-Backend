package com.smarttaskmanager.task.integration;

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
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.transaction.annotation.Transactional
class TaskDependencyIntegrationTests {

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
    private Task taskA;
    private Task taskB;
    private Task taskC;

    private String userAToken;
    private String userBToken;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(applicationProperties);

        userA = User.builder()
                .fullName("User A")
                .email("dep-a@example.com")
                .passwordHash(passwordEncoder.encode("TestPassword@123"))
                .role(Role.ROLE_USER)
                .enabled(true)
                .emailVerified(true)
                .build();
        userA = userRepository.save(userA);

        userB = User.builder()
                .fullName("User B")
                .email("dep-b@example.com")
                .passwordHash(passwordEncoder.encode("TestPassword@123"))
                .role(Role.ROLE_USER)
                .enabled(true)
                .emailVerified(true)
                .build();
        userB = userRepository.save(userB);

        taskA = Task.builder().user(userA).title("Task A").status(TaskStatus.TODO).priority(TaskPriority.HIGH).build();
        taskB = Task.builder().user(userA).title("Task B").status(TaskStatus.TODO).priority(TaskPriority.MEDIUM).build();
        taskC = Task.builder().user(userA).title("Task C").status(TaskStatus.COMPLETED).priority(TaskPriority.LOW).build();
        taskA = taskRepository.save(taskA);
        taskB = taskRepository.save(taskB);
        taskC = taskRepository.save(taskC);

        userAToken = jwtService.generateTokenWithExpiration(Map.of(), userA.getEmail(), applicationProperties.jwt().accessTokenExpirationMs());
        userBToken = jwtService.generateTokenWithExpiration(Map.of(), userB.getEmail(), applicationProperties.jwt().accessTokenExpirationMs());
    }

    // ==================== Unauthenticated Access Tests ====================

    @Test
    @DisplayName("Unauthenticated request to add dependency is rejected")
    void addDependency_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/tasks/" + taskA.getId() + "/dependencies/" + taskB.getId()))
                .andExpect(status().isUnauthorized());
    }

    // ==================== Add Dependency Tests ====================

    @Test
    @DisplayName("User can add a dependency")
    void addDependency_valid_returns200() throws Exception {
        mockMvc.perform(post("/api/v1/tasks/" + taskA.getId() + "/dependencies/" + taskB.getId())
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("Self-dependency is rejected")
    void addDependency_self_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/tasks/" + taskA.getId() + "/dependencies/" + taskA.getId())
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("Duplicate dependency is rejected")
    void addDependency_duplicate_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/tasks/" + taskA.getId() + "/dependencies/" + taskB.getId())
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/tasks/" + taskA.getId() + "/dependencies/" + taskB.getId())
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("Circular dependency is rejected")
    void addDependency_circular_returns400() throws Exception {
        // A depends on B
        mockMvc.perform(post("/api/v1/tasks/" + taskA.getId() + "/dependencies/" + taskB.getId())
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk());

        // B depends on A would create a cycle
        mockMvc.perform(post("/api/v1/tasks/" + taskB.getId() + "/dependencies/" + taskA.getId())
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("User A cannot add dependency on User B's task")
    void addDependency_crossUser_returns404() throws Exception {
        Task userBTask = Task.builder().user(userB).title("User B Task").status(TaskStatus.TODO).priority(TaskPriority.HIGH).build();
        userBTask = taskRepository.save(userBTask);

        mockMvc.perform(post("/api/v1/tasks/" + taskA.getId() + "/dependencies/" + userBTask.getId())
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // ==================== Remove Dependency Tests ====================

    @Test
    @DisplayName("User can remove a dependency")
    void removeDependency_valid_returns200() throws Exception {
        mockMvc.perform(post("/api/v1/tasks/" + taskA.getId() + "/dependencies/" + taskB.getId())
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/tasks/" + taskA.getId() + "/dependencies/" + taskB.getId())
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ==================== Dependency Status Tests ====================

    @Test
    @DisplayName("Dependency status shows all completed when dependencies are done")
    void getDependencyStatus_allCompleted_returns200() throws Exception {
        // A depends on C (which is COMPLETED)
        mockMvc.perform(post("/api/v1/tasks/" + taskA.getId() + "/dependencies/" + taskC.getId())
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tasks/" + taskA.getId() + "/dependencies/status")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allDependenciesCompleted").value(true))
                .andExpect(jsonPath("$.data.isBlocked").value(false));
    }

    @Test
    @DisplayName("Dependency status shows blocked when dependencies are not done")
    void getDependencyStatus_blocked_returns200() throws Exception {
        // A depends on B (which is TODO)
        mockMvc.perform(post("/api/v1/tasks/" + taskA.getId() + "/dependencies/" + taskB.getId())
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tasks/" + taskA.getId() + "/dependencies/status")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allDependenciesCompleted").value(false))
                .andExpect(jsonPath("$.data.isBlocked").value(true));
    }

    @Test
    @DisplayName("Dependency status shows not blocked when no dependencies")
    void getDependencyStatus_noDependencies_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/tasks/" + taskA.getId() + "/dependencies/status")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allDependenciesCompleted").value(true))
                .andExpect(jsonPath("$.data.isBlocked").value(false));
    }

    // ==================== Invalid JWT Tests ====================

    @Test
    @DisplayName("Invalid JWT cannot access dependency endpoints")
    void addDependency_invalidJwt_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/tasks/" + taskA.getId() + "/dependencies/" + taskB.getId())
                        .header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().isUnauthorized());
    }
}
