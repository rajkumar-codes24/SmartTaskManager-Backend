package com.smarttaskmanager.category.service;

import com.smarttaskmanager.category.dto.CategoryResponse;
import com.smarttaskmanager.category.dto.CreateCategoryRequest;
import com.smarttaskmanager.category.dto.UpdateCategoryRequest;
import com.smarttaskmanager.category.entity.Category;
import com.smarttaskmanager.category.repository.CategoryRepository;
import com.smarttaskmanager.common.exception.DuplicateResourceException;
import com.smarttaskmanager.common.exception.ResourceNotFoundException;
import com.smarttaskmanager.task.repository.TaskRepository;
import com.smarttaskmanager.user.entity.Role;
import com.smarttaskmanager.user.entity.User;
import com.smarttaskmanager.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceUnitTests {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private CategoryService categoryService;

    private User user;
    private Category category;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .fullName("Alice Smith")
                .email("alice@example.com")
                .passwordHash("$2a$12$hashedPassword")
                .role(Role.ROLE_USER)
                .emailVerified(true)
                .enabled(true)
                .build();

        category = Category.builder()
                .id(1L)
                .user(user)
                .name("Work")
                .description("Work-related tasks")
                .color("#5469D4")
                .build();

        // Set up security context with authenticated user
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(user.getEmail(), null);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // ==================== Create Category Tests ====================

    @Test
    @DisplayName("Create category succeeds with valid input")
    void createCategory_validInput_returnsResponse() {
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(categoryRepository.existsByUserIdAndNameIgnoreCase(user.getId(), "Work")).thenReturn(false);

        Category savedCategory = Category.builder()
                .id(1L)
                .user(user)
                .name("Work")
                .description("Work-related tasks")
                .color("#5469D4")
                .build();
        when(categoryRepository.save(any(Category.class))).thenReturn(savedCategory);

        CreateCategoryRequest request = new CreateCategoryRequest("Work", "Work-related tasks", "#5469D4");

        CategoryResponse response = categoryService.createCategory(request);

        assertThat(response.id()).isNotNull();
        assertThat(response.name()).isEqualTo("Work");
        assertThat(response.description()).isEqualTo("Work-related tasks");
        assertThat(response.color()).isEqualTo("#5469D4");

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(captor.capture());
        Category saved = captor.getValue();
        assertThat(saved.getUser().getId()).isEqualTo(user.getId());
        assertThat(saved.getName()).isEqualTo("Work");
    }

    @Test
    @DisplayName("Create category trims whitespace from name")
    void createCategory_trimsName() {
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(categoryRepository.existsByUserIdAndNameIgnoreCase(user.getId(), "Work")).thenReturn(false);

        Category savedCategory = Category.builder()
                .id(1L)
                .user(user)
                .name("Work")
                .build();
        when(categoryRepository.save(any(Category.class))).thenReturn(savedCategory);

        CreateCategoryRequest request = new CreateCategoryRequest("  Work  ", null, null);

        categoryService.createCategory(request);

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Work");
    }

    @Test
    @DisplayName("Create category rejects duplicate name (case-insensitive)")
    void createCategory_duplicateName_throwsDuplicateResourceException() {
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(categoryRepository.existsByUserIdAndNameIgnoreCase(user.getId(), "work")).thenReturn(true);

        CreateCategoryRequest request = new CreateCategoryRequest("work", null, null);

        assertThatThrownBy(() -> categoryService.createCategory(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Category already exists");
    }

    // ==================== Get User Categories Tests ====================

    @Test
    @DisplayName("Get user categories returns list")
    void getUserCategories_returnsList() {
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(categoryRepository.findByUserIdOrderByNameAsc(user.getId()))
                .thenReturn(List.of(category));

        List<CategoryResponse> responses = categoryService.getUserCategories();

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).name()).isEqualTo("Work");
    }

    @Test
    @DisplayName("Get user categories returns empty list when no categories")
    void getUserCategories_noCategories_returnsEmptyList() {
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(categoryRepository.findByUserIdOrderByNameAsc(user.getId())).thenReturn(List.of());

        List<CategoryResponse> responses = categoryService.getUserCategories();

        assertThat(responses).isEmpty();
    }

    // ==================== Get Category By ID Tests ====================

    @Test
    @DisplayName("Get category by ID succeeds for owned category")
    void getCategoryById_ownedCategory_returnsResponse() {
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(categoryRepository.findByIdAndUserId(1L, user.getId())).thenReturn(Optional.of(category));

        CategoryResponse response = categoryService.getCategoryById(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Work");
    }

    @Test
    @DisplayName("Get category by ID throws when category not found")
    void getCategoryById_notFound_throwsResourceNotFoundException() {
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(categoryRepository.findByIdAndUserId(999L, user.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getCategoryById(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Get category by ID throws when category belongs to another user")
    void getCategoryById_otherUser_throwsResourceNotFoundException() {
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(categoryRepository.findByIdAndUserId(1L, user.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getCategoryById(1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ==================== Update Category Tests ====================

    @Test
    @DisplayName("Update category name succeeds")
    void updateCategory_name_returnsUpdatedResponse() {
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(categoryRepository.findByIdAndUserId(1L, user.getId())).thenReturn(Optional.of(category));
        when(categoryRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(user.getId(), "Personal", 1L))
                .thenReturn(false);
        Category updatedCategory = Category.builder()
                .id(1L)
                .user(user)
                .name("Personal")
                .description("Work-related tasks")
                .color("#5469D4")
                .build();
        when(categoryRepository.save(any(Category.class))).thenReturn(updatedCategory);

        UpdateCategoryRequest request = new UpdateCategoryRequest("Personal", null, null);

        CategoryResponse response = categoryService.updateCategory(1L, request);

        assertThat(response.name()).isEqualTo("Personal");
    }

    @Test
    @DisplayName("Update category description succeeds")
    void updateCategory_description_returnsUpdatedResponse() {
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(categoryRepository.findByIdAndUserId(1L, user.getId())).thenReturn(Optional.of(category));
        Category updatedCategory = Category.builder()
                .id(1L)
                .user(user)
                .name("Work")
                .description("New description")
                .color("#5469D4")
                .build();
        when(categoryRepository.save(any(Category.class))).thenReturn(updatedCategory);

        UpdateCategoryRequest request = new UpdateCategoryRequest(null, "New description", null);

        CategoryResponse response = categoryService.updateCategory(1L, request);

        assertThat(response.description()).isEqualTo("New description");
    }

    @Test
    @DisplayName("Update category color succeeds")
    void updateCategory_color_returnsUpdatedResponse() {
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(categoryRepository.findByIdAndUserId(1L, user.getId())).thenReturn(Optional.of(category));
        Category updatedCategory = Category.builder()
                .id(1L)
                .user(user)
                .name("Work")
                .description("Work-related tasks")
                .color("#FF0000")
                .build();
        when(categoryRepository.save(any(Category.class))).thenReturn(updatedCategory);

        UpdateCategoryRequest request = new UpdateCategoryRequest(null, null, "#FF0000");

        CategoryResponse response = categoryService.updateCategory(1L, request);

        assertThat(response.color()).isEqualTo("#FF0000");
    }

    @Test
    @DisplayName("Update category rejects duplicate name")
    void updateCategory_duplicateName_throwsDuplicateResourceException() {
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(categoryRepository.findByIdAndUserId(1L, user.getId())).thenReturn(Optional.of(category));
        when(categoryRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(user.getId(), "Other", 1L))
                .thenReturn(true);

        UpdateCategoryRequest request = new UpdateCategoryRequest("Other", null, null);

        assertThatThrownBy(() -> categoryService.updateCategory(1L, request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("Update category trims name")
    void updateCategory_trimsName() {
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(categoryRepository.findByIdAndUserId(1L, user.getId())).thenReturn(Optional.of(category));
        when(categoryRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(user.getId(), "Personal", 1L))
                .thenReturn(false);
        Category updatedCategory = Category.builder()
                .id(1L)
                .user(user)
                .name("Personal")
                .build();
        when(categoryRepository.save(any(Category.class))).thenReturn(updatedCategory);

        UpdateCategoryRequest request = new UpdateCategoryRequest("  Personal  ", null, null);

        categoryService.updateCategory(1L, request);

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Personal");
    }

    // ==================== Delete Category Tests ====================

    @Test
    @DisplayName("Delete category succeeds")
    void deleteCategory_ownedCategory_succeeds() {
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(categoryRepository.findByIdAndUserId(1L, user.getId())).thenReturn(Optional.of(category));
        when(taskRepository.clearCategoryIdById(1L)).thenReturn(0);

        categoryService.deleteCategory(1L);

        verify(categoryRepository).delete(category);
    }

    @Test
    @DisplayName("Delete category detaches tasks before deletion")
    void deleteCategory_withTasks_detachesAndDeletes() {
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(categoryRepository.findByIdAndUserId(1L, user.getId())).thenReturn(Optional.of(category));
        when(taskRepository.clearCategoryIdById(1L)).thenReturn(3);

        categoryService.deleteCategory(1L);

        verify(taskRepository).clearCategoryIdById(1L);
        verify(categoryRepository).delete(category);
    }

    @Test
    @DisplayName("Delete category throws when not found")
    void deleteCategory_notFound_throwsResourceNotFoundException() {
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(categoryRepository.findByIdAndUserId(999L, user.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.deleteCategory(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
