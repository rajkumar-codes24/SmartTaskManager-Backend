package com.smarttaskmanager.task.integration;

import com.smarttaskmanager.category.entity.Category;
import com.smarttaskmanager.category.repository.CategoryRepository;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.transaction.annotation.Transactional
class TaskIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ApplicationProperties applicationProperties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JwtService jwtService;

    private User userA;
    private User userB;
    private Category userACategory;
    private Category userBCategory;
    private Task userATask;

    private String userAToken;
    private String userBToken;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(applicationProperties);

        // Create two users
        userA = User.builder()
                .fullName("User A")
                .email("task-a@example.com")
                .passwordHash(passwordEncoder.encode("TestPassword@123"))
                .role(Role.ROLE_USER)
                .enabled(true)
                .emailVerified(true)
                .build();
        userA = userRepository.save(userA);

        userB = User.builder()
                .fullName("User B")
                .email("task-b@example.com")
                .passwordHash(passwordEncoder.encode("TestPassword@123"))
                .role(Role.ROLE_USER)
                .enabled(true)
                .emailVerified(true)
                .build();
        userB = userRepository.save(userB);

        // Create categories
        userACategory = Category.builder()
                .user(userA)
                .name("Work")
                .description("Work tasks")
                .color("#5469D4")
                .build();
        userACategory = categoryRepository.save(userACategory);

        userBCategory = Category.builder()
                .user(userB)
                .name("Personal")
                .description("Personal tasks")
                .color("#FF0000")
                .build();
        userBCategory = categoryRepository.save(userBCategory);

        // Create a task for userA
        userATask = Task.builder()
                .user(userA)
                .category(userACategory)
                .title("Complete project")
                .description("Finish the SmartTaskManager project")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.HIGH)
                .dueDate(Instant.parse("2027-12-31T23:59:59Z"))
                .build();
        userATask = taskRepository.save(userATask);

        // Generate JWT tokens
        userAToken = jwtService.generateTokenWithExpiration(Map.of(), userA.getEmail(), applicationProperties.jwt().accessTokenExpirationMs());
        userBToken = jwtService.generateTokenWithExpiration(Map.of(), userB.getEmail(), applicationProperties.jwt().accessTokenExpirationMs());
    }

    // ==================== Unauthenticated Access Tests ====================

    @Test
    @DisplayName("Unauthenticated request to create task is rejected")
    void createTask_unauthenticated_returns401() throws Exception {
        String createJson = """
                {
                    "title": "New Task"
                }
                """;

        mockMvc.perform(post("/api/v1/tasks")
                        .contentType("application/json")
                        .content(createJson))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Unauthenticated request to list tasks is rejected")
    void listTasks_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/tasks"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Unauthenticated request to get task by ID is rejected")
    void getTaskById_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/tasks/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Unauthenticated request to update task is rejected")
    void updateTask_unauthenticated_returns401() throws Exception {
        String updateJson = """
                {
                    "title": "Updated"
                }
                """;

        mockMvc.perform(put("/api/v1/tasks/1")
                        .contentType("application/json")
                        .content(updateJson))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Unauthenticated request to delete task is rejected")
    void deleteTask_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/api/v1/tasks/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Unauthenticated request to get statistics is rejected")
    void getStatistics_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/tasks/statistics"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== Create Task Tests ====================

    @Test
    @DisplayName("Authenticated user can create a task")
    void createTask_authenticated_returns201() throws Exception {
        String createJson = """
                {
                    "title": "New Task",
                    "description": "A new task description",
                    "status": "IN_PROGRESS",
                    "priority": "HIGH"
                }
                """;

        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(createJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("New Task"))
                .andExpect(jsonPath("$.data.description").value("A new task description"))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.priority").value("HIGH"));
    }

    @Test
    @DisplayName("Create task with category")
    void createTask_withCategory_returns201() throws Exception {
        String createJson = """
                {
                    "title": "Categorized Task",
                    "categoryId": %d
                }
                """.formatted(userACategory.getId());

        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(createJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.category.id").value(userACategory.getId()))
                .andExpect(jsonPath("$.data.category.name").value("Work"));
    }

    @Test
    @DisplayName("Create task with other user's category is rejected")
    void createTask_withOtherUserCategory_returns404() throws Exception {
        String createJson = """
                {
                    "title": "Hacked Task",
                    "categoryId": %d
                }
                """.formatted(userBCategory.getId());

        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(createJson))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("Create task with blank title is rejected")
    void createTask_blankTitle_returns400() throws Exception {
        String createJson = """
                {
                    "title": "  "
                }
                """;

        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(createJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Create task with missing title is rejected")
    void createTask_missingTitle_returns400() throws Exception {
        String createJson = """
                {
                    "description": "No title"
                }
                """;

        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(createJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    // ==================== List Tasks Tests ====================

    @Test
    @DisplayName("User can list their own tasks")
    void listTasks_authenticated_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/tasks")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].title").value("Complete project"));
    }

    @Test
    @DisplayName("User A cannot see User B's tasks")
    void listTasks_userACannotSeeUserBTasks() throws Exception {
        // Create a task for userB
        Task userBTask = Task.builder()
                .user(userB)
                .title("User B Secret Task")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.LOW)
                .build();
        taskRepository.save(userBTask);

        mockMvc.perform(get("/api/v1/tasks")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andDo(result -> {
                    String responseBody = result.getResponse().getContentAsString();
                    assertThat(responseBody).doesNotContain("User B Secret Task");
                });
    }

    @Test
    @DisplayName("List tasks with status filter")
    void listTasks_withStatusFilter_returnsFiltered() throws Exception {
        mockMvc.perform(get("/api/v1/tasks")
                        .header("Authorization", "Bearer " + userAToken)
                        .param("status", "TODO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].status").value("TODO"));
    }

    @Test
    @DisplayName("List tasks with priority filter")
    void listTasks_withPriorityFilter_returnsFiltered() throws Exception {
        mockMvc.perform(get("/api/v1/tasks")
                        .header("Authorization", "Bearer " + userAToken)
                        .param("priority", "HIGH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].priority").value("HIGH"));
    }

    @Test
    @DisplayName("List tasks with search term")
    void listTasks_withSearchTerm_returnsMatched() throws Exception {
        mockMvc.perform(get("/api/v1/tasks")
                        .header("Authorization", "Bearer " + userAToken)
                        .param("search", "project"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].title").value("Complete project"));
    }

    @Test
    @DisplayName("List tasks with pagination")
    void listTasks_withPagination_returnsPageMetadata() throws Exception {
        mockMvc.perform(get("/api/v1/tasks")
                        .header("Authorization", "Bearer " + userAToken)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.number").value(0))
                .andExpect(jsonPath("$.data.size").value(10));
    }

    @Test
    @DisplayName("List tasks with sorting")
    void listTasks_withSorting_returnsSorted() throws Exception {
        mockMvc.perform(get("/api/v1/tasks")
                        .header("Authorization", "Bearer " + userAToken)
                        .param("sortBy", "title")
                        .param("sortDirection", "ASC"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("List tasks with categoryId filter")
    void listTasks_withCategoryIdFilter_returnsFiltered() throws Exception {
        mockMvc.perform(get("/api/v1/tasks")
                        .header("Authorization", "Bearer " + userAToken)
                        .param("categoryId", String.valueOf(userACategory.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].category.id").value(userACategory.getId()));
    }

    @Test
    @DisplayName("List tasks returns empty page when no tasks match")
    void listTasks_noMatch_returnsEmptyPage() throws Exception {
        mockMvc.perform(get("/api/v1/tasks")
                        .header("Authorization", "Bearer " + userAToken)
                        .param("search", "nonexistent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    // ==================== Get Task By ID Tests ====================

    @Test
    @DisplayName("User can get their own task by ID")
    void getTaskById_ownedTask_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/tasks/" + userATask.getId())
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(userATask.getId()))
                .andExpect(jsonPath("$.data.title").value("Complete project"));
    }

    @Test
    @DisplayName("User A cannot get User B's task by ID")
    void getTaskById_otherUserTask_returns404() throws Exception {
        Task userBTask = Task.builder()
                .user(userB)
                .title("User B Task")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.LOW)
                .build();
        taskRepository.save(userBTask);

        mockMvc.perform(get("/api/v1/tasks/" + userBTask.getId())
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("Get non-existent task returns 404")
    void getTaskById_nonExistent_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/tasks/9999")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // ==================== Update Task Tests ====================

    @Test
    @DisplayName("User can update their own task")
    void updateTask_ownedTask_returns200() throws Exception {
        String updateJson = """
                {
                    "title": "Updated Task Title",
                    "description": "Updated description",
                    "priority": "URGENT"
                }
                """;

        mockMvc.perform(put("/api/v1/tasks/" + userATask.getId())
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Updated Task Title"))
                .andExpect(jsonPath("$.data.description").value("Updated description"))
                .andExpect(jsonPath("$.data.priority").value("URGENT"));
    }

    @Test
    @DisplayName("User can mark task as completed")
    void updateTask_markCompleted_setsCompletedAt() throws Exception {
        String updateJson = """
                {
                    "status": "COMPLETED"
                }
                """;

        mockMvc.perform(put("/api/v1/tasks/" + userATask.getId())
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.completedAt").isNotEmpty());
    }

    @Test
    @DisplayName("User A cannot update User B's task")
    void updateTask_otherUserTask_returns404() throws Exception {
        Task userBTask = Task.builder()
                .user(userB)
                .title("User B Task")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.LOW)
                .build();
        taskRepository.save(userBTask);

        String updateJson = """
                {
                    "title": "Hacked"
                }
                """;

        mockMvc.perform(put("/api/v1/tasks/" + userBTask.getId())
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(updateJson))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("User cannot assign another user's category to a task")
    void updateTask_withOtherUserCategory_returns404() throws Exception {
        String updateJson = """
                {
                    "categoryId": %d
                }
                """.formatted(userBCategory.getId());

        mockMvc.perform(put("/api/v1/tasks/" + userATask.getId())
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(updateJson))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // ==================== Delete Task Tests ====================

    @Test
    @DisplayName("User can delete their own task")
    void deleteTask_ownedTask_returns200() throws Exception {
        mockMvc.perform(delete("/api/v1/tasks/" + userATask.getId())
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(taskRepository.findById(userATask.getId())).isEmpty();
    }

    @Test
    @DisplayName("User A cannot delete User B's task")
    void deleteTask_otherUserTask_returns404() throws Exception {
        Task userBTask = Task.builder()
                .user(userB)
                .title("User B Task")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.LOW)
                .build();
        taskRepository.save(userBTask);

        mockMvc.perform(delete("/api/v1/tasks/" + userBTask.getId())
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // ==================== Statistics Tests ====================

    @Test
    @DisplayName("User can get their own task statistics")
    void getStatistics_authenticated_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/tasks/statistics")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalTasks").value(1))
                .andExpect(jsonPath("$.data.completedTasks").value(0))
                .andExpect(jsonPath("$.data.pendingTasks").value(1))
                .andExpect(jsonPath("$.data.byStatus").isMap())
                .andExpect(jsonPath("$.data.byPriority").isMap());
    }

    @Test
    @DisplayName("Statistics are isolated by user")
    void getStatistics_isolatedByUser() throws Exception {
        // User B has no tasks
        mockMvc.perform(get("/api/v1/tasks/statistics")
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalTasks").value(0))
                .andExpect(jsonPath("$.data.completedTasks").value(0))
                .andExpect(jsonPath("$.data.pendingTasks").value(0));
    }

    // ==================== Invalid JWT Tests ====================

    @Test
    @DisplayName("Invalid JWT cannot access task endpoints")
    void createTask_invalidJwt_returns401() throws Exception {
        String createJson = """
                {
                    "title": "Hacked Task"
                }
                """;

        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer invalid.token.here")
                        .contentType("application/json")
                        .content(createJson))
                .andExpect(status().isUnauthorized());
    }

    // ==================== Malformed Request Tests ====================

    @Test
    @DisplayName("Malformed JSON returns 400")
    void createTask_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content("{ invalid json }"))
                .andExpect(status().isBadRequest());
    }
}
