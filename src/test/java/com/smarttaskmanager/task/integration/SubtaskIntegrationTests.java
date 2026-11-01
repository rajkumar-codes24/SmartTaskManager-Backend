package com.smarttaskmanager.task.integration;

import com.smarttaskmanager.common.config.ApplicationProperties;
import com.smarttaskmanager.security.jwt.JwtService;
import com.smarttaskmanager.task.entity.Subtask;
import com.smarttaskmanager.task.entity.Task;
import com.smarttaskmanager.task.entity.TaskPriority;
import com.smarttaskmanager.task.entity.TaskStatus;
import com.smarttaskmanager.task.repository.SubtaskRepository;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.transaction.annotation.Transactional
class SubtaskIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private SubtaskRepository subtaskRepository;

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

    private String userAToken;
    private String userBToken;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(applicationProperties);

        userA = User.builder()
                .fullName("User A")
                .email("subtask-a@example.com")
                .passwordHash(passwordEncoder.encode("TestPassword@123"))
                .role(Role.ROLE_USER)
                .enabled(true)
                .emailVerified(true)
                .build();
        userA = userRepository.save(userA);

        userB = User.builder()
                .fullName("User B")
                .email("subtask-b@example.com")
                .passwordHash(passwordEncoder.encode("TestPassword@123"))
                .role(Role.ROLE_USER)
                .enabled(true)
                .emailVerified(true)
                .build();
        userB = userRepository.save(userB);

        userATask = Task.builder()
                .user(userA)
                .title("User A Task")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.HIGH)
                .build();
        userATask = taskRepository.save(userATask);

        userBTask = Task.builder()
                .user(userB)
                .title("User B Task")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.MEDIUM)
                .build();
        userBTask = taskRepository.save(userBTask);

        userAToken = jwtService.generateTokenWithExpiration(Map.of(), userA.getEmail(), applicationProperties.jwt().accessTokenExpirationMs());
        userBToken = jwtService.generateTokenWithExpiration(Map.of(), userB.getEmail(), applicationProperties.jwt().accessTokenExpirationMs());
    }

    // ==================== Unauthenticated Access Tests ====================

    @Test
    @DisplayName("Unauthenticated request to create subtask is rejected")
    void createSubtask_unauthenticated_returns401() throws Exception {
        String json = """
                {"title": "Test subtask"}
                """;
        mockMvc.perform(post("/api/v1/tasks/1/subtasks")
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Unauthenticated request to list subtasks is rejected")
    void listSubtasks_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/tasks/1/subtasks"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== Create Subtask Tests ====================

    @Test
    @DisplayName("Authenticated user can create a subtask")
    void createSubtask_valid_returns201() throws Exception {
        String json = """
                {
                    "title": "Research requirements",
                    "description": "Gather project requirements",
                    "priority": "HIGH",
                    "dueDate": "2027-01-01T10:00:00Z"
                }
                """;
        mockMvc.perform(post("/api/v1/tasks/" + userATask.getId() + "/subtasks")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Research requirements"))
                .andExpect(jsonPath("$.data.status").value("TODO"))
                .andExpect(jsonPath("$.data.position").value(0));
    }

    @Test
    @DisplayName("Create subtask with blank title is rejected")
    void createSubtask_blankTitle_returns400() throws Exception {
        String json = """
                {"title": "  "}
                """;
        mockMvc.perform(post("/api/v1/tasks/" + userATask.getId() + "/subtasks")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Create subtask for non-existent task returns 404")
    void createSubtask_nonExistentTask_returns404() throws Exception {
        String json = """
                {"title": "Orphan subtask"}
                """;
        mockMvc.perform(post("/api/v1/tasks/9999/subtasks")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("User A cannot create subtask for User B's task")
    void createSubtask_otherUserTask_returns404() throws Exception {
        String json = """
                {"title": "Hacked subtask"}
                """;
        mockMvc.perform(post("/api/v1/tasks/" + userBTask.getId() + "/subtasks")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("Multiple subtasks get incrementing positions")
    void createSubtask_multiple_returnsIncrementingPositions() throws Exception {
        String json1 = """
                {"title": "First subtask"}
                """;
        String json2 = """
                {"title": "Second subtask"}
                """;

        MvcResult result1 = mockMvc.perform(post("/api/v1/tasks/" + userATask.getId() + "/subtasks")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(json1))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult result2 = mockMvc.perform(post("/api/v1/tasks/" + userATask.getId() + "/subtasks")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(json2))
                .andExpect(status().isCreated())
                .andReturn();

        int pos1 = objectMapper.readTree(result1.getResponse().getContentAsString()).path("data").path("position").asInt();
        int pos2 = objectMapper.readTree(result2.getResponse().getContentAsString()).path("data").path("position").asInt();
        assertThat(pos2).isGreaterThan(pos1);
    }

    // ==================== List Subtasks Tests ====================

    @Test
    @DisplayName("User can list their subtasks")
    void listSubtasks_authenticated_returns200() throws Exception {
        // Create a subtask first
        String json = """
                {"title": "List me"}
                """;
        mockMvc.perform(post("/api/v1/tasks/" + userATask.getId() + "/subtasks")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/tasks/" + userATask.getId() + "/subtasks")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isNotEmpty())
                .andExpect(jsonPath("$.data[0].title").value("List me"));
    }

    @Test
    @DisplayName("User A cannot list User B's subtasks")
    void listSubtasks_otherUserTask_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/tasks/" + userBTask.getId() + "/subtasks")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // ==================== Get Subtask by ID Tests ====================

    @Test
    @DisplayName("User can get their own subtask by ID")
    void getSubtaskById_owned_returns200() throws Exception {
        String json = """
                {"title": "Get me"}
                """;
        MvcResult result = mockMvc.perform(post("/api/v1/tasks/" + userATask.getId() + "/subtasks")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();

        Long subtaskId = objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(get("/api/v1/tasks/" + userATask.getId() + "/subtasks/" + subtaskId)
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(subtaskId))
                .andExpect(jsonPath("$.data.title").value("Get me"));
    }

    @Test
    @DisplayName("Get non-existent subtask returns 404")
    void getSubtaskById_nonExistent_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/tasks/" + userATask.getId() + "/subtasks/9999")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // ==================== Update Subtask Tests ====================

    @Test
    @DisplayName("User can update their own subtask")
    void updateSubtask_owned_returns200() throws Exception {
        String json = """
                {"title": "Create me"}
                """;
        MvcResult result = mockMvc.perform(post("/api/v1/tasks/" + userATask.getId() + "/subtasks")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();

        Long subtaskId = objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();

        String updateJson = """
                {
                    "title": "Updated title",
                    "description": "Updated description",
                    "priority": "URGENT"
                }
                """;
        mockMvc.perform(put("/api/v1/tasks/" + userATask.getId() + "/subtasks/" + subtaskId)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Updated title"))
                .andExpect(jsonPath("$.data.description").value("Updated description"))
                .andExpect(jsonPath("$.data.priority").value("URGENT"));
    }

    @Test
    @DisplayName("Marking subtask COMPLETED sets completedAt")
    void updateSubtask_completed_setsCompletedAt() throws Exception {
        String json = """
                {"title": "Complete me"}
                """;
        MvcResult result = mockMvc.perform(post("/api/v1/tasks/" + userATask.getId() + "/subtasks")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();

        Long subtaskId = objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();

        String updateJson = """
                {"status": "COMPLETED"}
                """;
        mockMvc.perform(put("/api/v1/tasks/" + userATask.getId() + "/subtasks/" + subtaskId)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.completedAt").isNotEmpty());
    }

    @Test
    @DisplayName("Reopening subtask clears completedAt")
    void updateSubtask_reopened_clearsCompletedAt() throws Exception {
        String json = """
                {"title": "Reopen me", "status": "COMPLETED"}
                """;
        MvcResult result = mockMvc.perform(post("/api/v1/tasks/" + userATask.getId() + "/subtasks")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();

        Long subtaskId = objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();

        String updateJson = """
                {"status": "TODO"}
                """;
        mockMvc.perform(put("/api/v1/tasks/" + userATask.getId() + "/subtasks/" + subtaskId)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("TODO"))
                .andExpect(jsonPath("$.data.completedAt").isEmpty());
    }

    // ==================== Delete Subtask Tests ====================

    @Test
    @DisplayName("User can delete their own subtask")
    void deleteSubtask_owned_returns200() throws Exception {
        String json = """
                {"title": "Delete me"}
                """;
        MvcResult result = mockMvc.perform(post("/api/v1/tasks/" + userATask.getId() + "/subtasks")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();

        Long subtaskId = objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(delete("/api/v1/tasks/" + userATask.getId() + "/subtasks/" + subtaskId)
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(subtaskRepository.findByIdAndUserId(subtaskId, userA.getId())).isNull();
    }

    @Test
    @DisplayName("User A cannot delete User B's subtask")
    void deleteSubtask_otherUser_returns404() throws Exception {
        String json = """
                {"title": "User B subtask"}
                """;
        mockMvc.perform(post("/api/v1/tasks/" + userBTask.getId() + "/subtasks")
                        .header("Authorization", "Bearer " + userBToken)
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/tasks/" + userBTask.getId() + "/subtasks/9999")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // ==================== Completion Progress Tests ====================

    @Test
    @DisplayName("Completion progress is calculated correctly")
    void completionProgress_calculatedCorrectly() throws Exception {
        // Create 3 subtasks
        for (int i = 0; i < 3; i++) {
            String json = String.format("{\"title\": \"Subtask %d\"}", i);
            mockMvc.perform(post("/api/v1/tasks/" + userATask.getId() + "/subtasks")
                            .header("Authorization", "Bearer " + userAToken)
                            .contentType("application/json")
                            .content(json))
                    .andExpect(status().isCreated());
        }

        // Complete 1 of 3
        ListSubtasksAndCompleteOne();

        // Verify via service
        long total = subtaskRepository.countByParentTaskIdAndUserId(userATask.getId(), userA.getId());
        long completed = subtaskRepository.countCompletedByParentTaskIdAndUserId(userATask.getId(), userA.getId());
        assertThat(total).isEqualTo(3);
        assertThat(completed).isEqualTo(1);
    }

    void ListSubtasksAndCompleteOne() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/tasks/" + userATask.getId() + "/subtasks")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        Long firstSubtaskId = objectMapper.readTree(body).path("data").get(0).path("id").asLong();

        String updateJson = """
                {"status": "COMPLETED"}
                """;
        mockMvc.perform(put("/api/v1/tasks/" + userATask.getId() + "/subtasks/" + firstSubtaskId)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(updateJson))
                .andExpect(status().isOk());
    }

    // ==================== Invalid JWT Tests ====================

    @Test
    @DisplayName("Invalid JWT cannot access subtask endpoints")
    void createSubtask_invalidJwt_returns401() throws Exception {
        String json = """
                {"title": "Hacked"}
                """;
        mockMvc.perform(post("/api/v1/tasks/" + userATask.getId() + "/subtasks")
                        .header("Authorization", "Bearer invalid.token.here")
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isUnauthorized());
    }
}
