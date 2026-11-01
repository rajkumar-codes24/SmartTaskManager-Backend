package com.smarttaskmanager.category.integration;

import com.smarttaskmanager.category.entity.Category;
import com.smarttaskmanager.category.repository.CategoryRepository;
import com.smarttaskmanager.common.config.ApplicationProperties;
import com.smarttaskmanager.security.jwt.JwtService;
import com.smarttaskmanager.user.entity.Role;
import com.smarttaskmanager.user.entity.User;
import com.smarttaskmanager.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.transaction.annotation.Transactional
class CategoryIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

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

    private String userAToken;
    private String userBToken;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(applicationProperties);

        // Create two users
        userA = User.builder()
                .fullName("User A")
                .email("category-a@example.com")
                .passwordHash(passwordEncoder.encode("TestPassword@123"))
                .role(Role.ROLE_USER)
                .enabled(true)
                .emailVerified(true)
                .build();
        userA = userRepository.save(userA);

        userB = User.builder()
                .fullName("User B")
                .email("category-b@example.com")
                .passwordHash(passwordEncoder.encode("TestPassword@123"))
                .role(Role.ROLE_USER)
                .enabled(true)
                .emailVerified(true)
                .build();
        userB = userRepository.save(userB);

        // Create categories for each user
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

        // Generate JWT tokens using the method that accepts email directly
        userAToken = jwtService.generateTokenWithExpiration(Map.of(), userA.getEmail(), applicationProperties.jwt().accessTokenExpirationMs());
        userBToken = jwtService.generateTokenWithExpiration(Map.of(), userB.getEmail(), applicationProperties.jwt().accessTokenExpirationMs());
    }

    // ==================== Unauthenticated Access Tests ====================

    @Test
    @DisplayName("Unauthenticated request to create category is rejected")
    void createCategory_unauthenticated_returns401() throws Exception {
        String createJson = """
                {
                    "name": "New Category",
                    "description": "A new category"
                }
                """;

        mockMvc.perform(post("/api/v1/categories")
                        .contentType("application/json")
                        .content(createJson))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Unauthenticated request to list categories is rejected")
    void listCategories_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Unauthenticated request to get category by ID is rejected")
    void getCategoryById_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/categories/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Unauthenticated request to update category is rejected")
    void updateCategory_unauthenticated_returns401() throws Exception {
        String updateJson = """
                {
                    "name": "Updated Name"
                }
                """;

        mockMvc.perform(put("/api/v1/categories/1")
                        .contentType("application/json")
                        .content(updateJson))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Unauthenticated request to delete category is rejected")
    void deleteCategory_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/api/v1/categories/1"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== Create Category Tests ====================

    @Test
    @DisplayName("Authenticated user can create a category")
    void createCategory_authenticated_returns201() throws Exception {
        String createJson = """
                {
                    "name": "Health",
                    "description": "Health and fitness tasks",
                    "color": "#00FF00"
                }
                """;

        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(createJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Health"))
                .andExpect(jsonPath("$.data.description").value("Health and fitness tasks"))
                .andExpect(jsonPath("$.data.color").value("#00FF00"));
    }

    @Test
    @DisplayName("Create category with blank name is rejected")
    void createCategory_blankName_returns400() throws Exception {
        String createJson = """
                {
                    "name": "  "
                }
                """;

        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(createJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Create category with missing name is rejected")
    void createCategory_missingName_returns400() throws Exception {
        String createJson = """
                {
                    "description": "No name provided"
                }
                """;

        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(createJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Create category with duplicate name is rejected")
    void createCategory_duplicateName_returns409() throws Exception {
        String createJson = """
                {
                    "name": "Work"
                }
                """;

        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(createJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_RESOURCE"));
    }

    @Test
    @DisplayName("Create category with duplicate name (case-insensitive) is rejected")
    void createCategory_duplicateNameCaseInsensitive_returns409() throws Exception {
        String createJson = """
                {
                    "name": "work"
                }
                """;

        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(createJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_RESOURCE"));
    }

    @Test
    @DisplayName("Different users can have categories with the same name")
    void createCategory_sameNameDifferentUser_returns201() throws Exception {
        String createJson = """
                {
                    "name": "Work"
                }
                """;

        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + userBToken)
                        .contentType("application/json")
                        .content(createJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Work"));
    }

    // ==================== List Categories Tests ====================

    @Test
    @DisplayName("User can list their own categories")
    void listCategories_authenticated_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/categories")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isNotEmpty())
                .andExpect(jsonPath("$.data[0].name").value("Work"));
    }

    @Test
    @DisplayName("User A cannot see User B's categories")
    void listCategories_userACannotSeeUserBCategories() throws Exception {
        mockMvc.perform(get("/api/v1/categories")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andDo(result -> {
                    String responseBody = result.getResponse().getContentAsString();
                    assertThat(responseBody).doesNotContain("Personal");
                });
    }

    // ==================== Get Category By ID Tests ====================

    @Test
    @DisplayName("User can get their own category by ID")
    void getCategoryById_ownedCategory_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/categories/" + userACategory.getId())
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(userACategory.getId()))
                .andExpect(jsonPath("$.data.name").value("Work"));
    }

    @Test
    @DisplayName("User A cannot get User B's category by ID")
    void getCategoryById_otherUserCategory_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/categories/" + userBCategory.getId())
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("Get non-existent category returns 404")
    void getCategoryById_nonExistent_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/categories/9999")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // ==================== Update Category Tests ====================

    @Test
    @DisplayName("User can update their own category")
    void updateCategory_ownedCategory_returns200() throws Exception {
        String updateJson = """
                {
                    "name": "Updated Work",
                    "description": "Updated description",
                    "color": "#0000FF"
                }
                """;

        mockMvc.perform(put("/api/v1/categories/" + userACategory.getId())
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Updated Work"))
                .andExpect(jsonPath("$.data.description").value("Updated description"))
                .andExpect(jsonPath("$.data.color").value("#0000FF"));
    }

    @Test
    @DisplayName("User can update only specific fields")
    void updateCategory_partialUpdate_returns200() throws Exception {
        String updateJson = """
                {
                    "description": "Only description changed"
                }
                """;

        mockMvc.perform(put("/api/v1/categories/" + userACategory.getId())
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Work"))
                .andExpect(jsonPath("$.data.description").value("Only description changed"));
    }

    @Test
    @DisplayName("User A cannot update User B's category")
    void updateCategory_otherUserCategory_returns404() throws Exception {
        String updateJson = """
                {
                    "name": "Hacked"
                }
                """;

        mockMvc.perform(put("/api/v1/categories/" + userBCategory.getId())
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(updateJson))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("Update category to duplicate name is rejected")
    void updateCategory_duplicateName_returns409() throws Exception {
        // First create another category for userA
        String createJson = """
                {
                    "name": "Second"
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(createJson))
                .andExpect(status().isCreated())
                .andReturn();

        // Extract the ID of the newly created "Second" category
        String responseBody = createResult.getResponse().getContentAsString();
        Long secondCategoryId = objectMapper.readTree(responseBody).path("data").path("id").asLong();

        // Then try to rename "Second" to "Work" (which already exists for userA)
        String updateJson = """
                {
                    "name": "Work"
                }
                """;

        mockMvc.perform(put("/api/v1/categories/" + secondCategoryId)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(updateJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_RESOURCE"));
    }

    // ==================== Delete Category Tests ====================

    @Test
    @DisplayName("User can delete their own category")
    void deleteCategory_ownedCategory_returns200() throws Exception {
        mockMvc.perform(delete("/api/v1/categories/" + userACategory.getId())
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Verify category is deleted
        assertThat(categoryRepository.findById(userACategory.getId())).isEmpty();
    }

    @Test
    @DisplayName("User A cannot delete User B's category")
    void deleteCategory_otherUserCategory_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/categories/" + userBCategory.getId())
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("Delete non-existent category returns 404")
    void deleteCategory_nonExistent_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/categories/9999")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // ==================== Invalid JWT Tests ====================

    @Test
    @DisplayName("Invalid JWT cannot access category endpoints")
    void createCategory_invalidJwt_returns401() throws Exception {
        String createJson = """
                {
                    "name": "Hacked"
                }
                """;

        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer invalid.token.here")
                        .contentType("application/json")
                        .content(createJson))
                .andExpect(status().isUnauthorized());
    }

    // ==================== Malformed Request Tests ====================

    @Test
    @DisplayName("Malformed JSON returns 400")
    void createCategory_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content("{ invalid json }"))
                .andExpect(status().isBadRequest());
    }
}
