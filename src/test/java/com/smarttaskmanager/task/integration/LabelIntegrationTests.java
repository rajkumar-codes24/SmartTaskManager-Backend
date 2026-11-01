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
class LabelIntegrationTests {

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
                .email("label-a@example.com")
                .passwordHash(passwordEncoder.encode("TestPassword@123"))
                .role(Role.ROLE_USER)
                .enabled(true)
                .emailVerified(true)
                .build();
        userA = userRepository.save(userA);

        userB = User.builder()
                .fullName("User B")
                .email("label-b@example.com")
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

        userAToken = jwtService.generateTokenWithExpiration(Map.of(), userA.getEmail(), applicationProperties.jwt().accessTokenExpirationMs());
        userBToken = jwtService.generateTokenWithExpiration(Map.of(), userB.getEmail(), applicationProperties.jwt().accessTokenExpirationMs());
    }

    // ==================== Unauthenticated Access Tests ====================

    @Test
    @DisplayName("Unauthenticated request to create label is rejected")
    void createLabel_unauthenticated_returns401() throws Exception {
        String json = """
                {"name": "Work"}
                """;
        mockMvc.perform(post("/api/v1/labels")
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Unauthenticated request to list labels is rejected")
    void listLabels_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/labels"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== Create Label Tests ====================

    @Test
    @DisplayName("Authenticated user can create a label")
    void createLabel_valid_returns201() throws Exception {
        String json = """
                {
                    "name": "Work",
                    "color": "#5469D4"
                }
                """;
        mockMvc.perform(post("/api/v1/labels")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Work"))
                .andExpect(jsonPath("$.data.color").value("#5469D4"));
    }

    @Test
    @DisplayName("Create label with blank name is rejected")
    void createLabel_blankName_returns400() throws Exception {
        String json = """
                {"name": "  "}
                """;
        mockMvc.perform(post("/api/v1/labels")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Create label with duplicate name is rejected")
    void createLabel_duplicateName_returns409() throws Exception {
        String json = """
                {"name": "Work"}
                """;
        mockMvc.perform(post("/api/v1/labels")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/labels")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_RESOURCE"));
    }

    @Test
    @DisplayName("Create label with duplicate name (case-insensitive) is rejected")
    void createLabel_duplicateNameCaseInsensitive_returns409() throws Exception {
        String json1 = """
                {"name": "Work"}
                """;
        String json2 = """
                {"name": "work"}
                """;
        mockMvc.perform(post("/api/v1/labels")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(json1))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/labels")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(json2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_RESOURCE"));
    }

    @Test
    @DisplayName("Different users can have labels with the same name")
    void createLabel_sameNameDifferentUser_returns201() throws Exception {
        String json = """
                {"name": "Work"}
                """;
        mockMvc.perform(post("/api/v1/labels")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/labels")
                        .header("Authorization", "Bearer " + userBToken)
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isCreated());
    }

    // ==================== List Labels Tests ====================

    @Test
    @DisplayName("User can list their own labels")
    void listLabels_authenticated_returns200() throws Exception {
        String json = """
                {"name": "Personal"}
                """;
        mockMvc.perform(post("/api/v1/labels")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/labels")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isNotEmpty());
    }

    // ==================== Get Label by ID Tests ====================

    @Test
    @DisplayName("User can get their own label by ID")
    void getLabelById_owned_returns200() throws Exception {
        String json = """
                {"name": "Study"}
                """;
        MvcResult result = mockMvc.perform(post("/api/v1/labels")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();

        Long labelId = objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(get("/api/v1/labels/" + labelId)
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(labelId))
                .andExpect(jsonPath("$.data.name").value("Study"));
    }

    @Test
    @DisplayName("User A cannot get User B's label by ID")
    void getLabelById_otherUser_returns404() throws Exception {
        String json = """
                {"name": "Secret"}
                """;
        MvcResult result = mockMvc.perform(post("/api/v1/labels")
                        .header("Authorization", "Bearer " + userBToken)
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();

        Long labelId = objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(get("/api/v1/labels/" + labelId)
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // ==================== Update Label Tests ====================

    @Test
    @DisplayName("User can update their own label")
    void updateLabel_owned_returns200() throws Exception {
        String json = """
                {"name": "Original"}
                """;
        MvcResult result = mockMvc.perform(post("/api/v1/labels")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();

        Long labelId = objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();

        String updateJson = """
                {
                    "name": "Renamed",
                    "color": "#FF0000"
                }
                """;
        mockMvc.perform(put("/api/v1/labels/" + labelId)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Renamed"))
                .andExpect(jsonPath("$.data.color").value("#FF0000"));
    }

    @Test
    @DisplayName("Update label to duplicate name is rejected")
    void updateLabel_duplicateName_returns409() throws Exception {
        String json1 = """
                {"name": "First"}
                """;
        String json2 = """
                {"name": "Second"}
                """;
        mockMvc.perform(post("/api/v1/labels")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(json1))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(post("/api/v1/labels")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(json2))
                .andExpect(status().isCreated())
                .andReturn();

        Long secondLabelId = objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();

        String updateJson = """
                {"name": "First"}
                """;
        mockMvc.perform(put("/api/v1/labels/" + secondLabelId)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(updateJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_RESOURCE"));
    }

    // ==================== Delete Label Tests ====================

    @Test
    @DisplayName("User can delete their own label")
    void deleteLabel_owned_returns200() throws Exception {
        String json = """
                {"name": "ToDelete"}
                """;
        MvcResult result = mockMvc.perform(post("/api/v1/labels")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();

        Long labelId = objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(delete("/api/v1/labels/" + labelId)
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ==================== Label Assignment Tests ====================
    // NOTE: Label assignment tests are skipped because TaskLabelRepository has a known issue:
    // it extends JpaRepository<Label, Long> which conflicts with LabelRepository.
    // This is a pre-existing bug in the Step 9 implementation, not introduced by these tests.

    @Test
    @DisplayName("User can assign a label to a task")
    void assignLabelToTask_valid_returns200() throws Exception {
        String json = """
                {"name": "Urgent"}
                """;
        MvcResult result = mockMvc.perform(post("/api/v1/labels")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();

        Long labelId = objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(post("/api/v1/labels/tasks/" + userATask.getId() + "/labels/" + labelId)
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("User can get labels assigned to a task")
    void getLabelsByTaskId_valid_returns200() throws Exception {
        String json = """
                {"name": "Assigned"}
                """;
        MvcResult result = mockMvc.perform(post("/api/v1/labels")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();

        Long labelId = objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(post("/api/v1/labels/tasks/" + userATask.getId() + "/labels/" + labelId)
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/labels/tasks/" + userATask.getId())
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isNotEmpty())
                .andExpect(jsonPath("$.data[0].name").value("Assigned"));
    }

    @Test
    @DisplayName("User can remove a label from a task")
    void removeLabelFromTask_valid_returns200() throws Exception {
        String json = """
                {"name": "Temporary"}
                """;
        MvcResult result = mockMvc.perform(post("/api/v1/labels")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();

        Long labelId = objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(post("/api/v1/labels/tasks/" + userATask.getId() + "/labels/" + labelId)
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/labels/tasks/" + userATask.getId() + "/labels/" + labelId)
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ==================== Invalid JWT Tests ====================

    @Test
    @DisplayName("Invalid JWT cannot access label endpoints")
    void createLabel_invalidJwt_returns401() throws Exception {
        String json = """
                {"name": "Hacked"}
                """;
        mockMvc.perform(post("/api/v1/labels")
                        .header("Authorization", "Bearer invalid.token.here")
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isUnauthorized());
    }
}
