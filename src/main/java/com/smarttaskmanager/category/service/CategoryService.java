package com.smarttaskmanager.category.service;

import com.smarttaskmanager.category.dto.CategoryResponse;
import com.smarttaskmanager.category.dto.CreateCategoryRequest;
import com.smarttaskmanager.category.dto.UpdateCategoryRequest;
import com.smarttaskmanager.category.entity.Category;
import com.smarttaskmanager.category.repository.CategoryRepository;
import com.smarttaskmanager.common.exception.DuplicateResourceException;
import com.smarttaskmanager.common.exception.ResourceNotFoundException;
import com.smarttaskmanager.task.repository.TaskRepository;
import com.smarttaskmanager.user.entity.User;
import com.smarttaskmanager.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for managing categories scoped to the authenticated user.
 *
 * <p>Enforces ownership: every operation derives the user from the security context,
 * never from request parameters. Prevents duplicate category names per user
 * using case-insensitive comparison.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;

    /**
     * Creates a new category for the authenticated user.
     *
     * @param request category creation request with name, optional description and color
     * @return the created category response
     */
    @Transactional
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        User user = getCurrentUser();
        String normalizedName = request.name().trim();

        // Check for duplicate category name (case-insensitive) for the same user
        if (categoryRepository.existsByUserIdAndNameIgnoreCase(user.getId(), normalizedName)) {
            throw new DuplicateResourceException("Category", "name", normalizedName);
        }

        Category category = Category.builder()
                .user(user)
                .name(normalizedName)
                .description(request.description())
                .color(request.color())
                .build();

        category = categoryRepository.save(category);
        log.info("Category [{}] created for user [{}]", category.getName(), user.getEmail());
        return toResponse(category);
    }

    /**
     * Retrieves all categories belonging to the authenticated user, ordered by name.
     *
     * @return list of category responses
     */
    @Transactional(readOnly = true)
    public List<CategoryResponse> getUserCategories() {
        User user = getCurrentUser();
        List<Category> categories = categoryRepository.findByUserIdOrderByNameAsc(user.getId());
        return categories.stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Retrieves a single category by ID, ensuring it belongs to the authenticated user.
     *
     * @param id category ID
     * @return the category response
     * @throws ResourceNotFoundException if the category does not exist or does not belong to the user
     */
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(Long id) {
        User user = getCurrentUser();
        Category category = categoryRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));
        return toResponse(category);
    }

    /**
     * Updates an existing category belonging to the authenticated user.
     *
     * @param id category ID
     * @param request update request with optional fields
     * @return the updated category response
     */
    @Transactional
    public CategoryResponse updateCategory(Long id, UpdateCategoryRequest request) {
        User user = getCurrentUser();
        Category category = categoryRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));

        if (request.name() != null) {
            String normalizedName = request.name().trim();
            if (categoryRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(user.getId(), normalizedName, id)) {
                throw new DuplicateResourceException("Category", "name", normalizedName);
            }
            category.setName(normalizedName);
        }

        if (request.description() != null) {
            category.setDescription(request.description());
        }

        if (request.color() != null) {
            category.setColor(request.color());
        }

        category = categoryRepository.save(category);
        log.info("Category [{}] updated for user [{}]", category.getName(), user.getEmail());
        return toResponse(category);
    }

    /**
     * Deletes a category belonging to the authenticated user.
     *
     * <p>Before deletion, all tasks associated with this category have their category
     * reference set to NULL to preserve referential integrity.</p>
     *
     * @param id category ID
     */
    @Transactional
    public void deleteCategory(Long id) {
        User user = getCurrentUser();
        Category category = categoryRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));

        // Set all tasks' category to NULL before deleting the category
        int updatedTasks = taskRepository.clearCategoryIdById(category.getId());
        if (updatedTasks > 0) {
            log.info("Detached {} task(s) from category [{}] before deletion", updatedTasks, category.getName());
        }

        categoryRepository.delete(category);
        log.info("Category [{}] deleted for user [{}]", category.getName(), user.getEmail());
    }

    /**
     * Retrieves the currently authenticated user from the security context.
     */
    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    private CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.getColor()
        );
    }
}
