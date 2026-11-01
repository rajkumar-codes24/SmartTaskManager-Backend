package com.smarttaskmanager.task.service;

import com.smarttaskmanager.category.entity.Category;
import com.smarttaskmanager.category.repository.CategoryRepository;
import com.smarttaskmanager.common.exception.ResourceNotFoundException;
import com.smarttaskmanager.task.dto.CreateTaskRequest;
import com.smarttaskmanager.task.dto.TaskResponse;
import com.smarttaskmanager.task.dto.TaskStatisticsResponse;
import com.smarttaskmanager.task.dto.UpdateTaskRequest;
import com.smarttaskmanager.task.entity.Task;
import com.smarttaskmanager.task.entity.TaskPriority;
import com.smarttaskmanager.task.entity.TaskStatus;
import com.smarttaskmanager.task.repository.TaskRepository;
import com.smarttaskmanager.task.repository.TaskRepository.PriorityCountProjection;
import com.smarttaskmanager.task.repository.TaskRepository.StatusCountProjection;
import com.smarttaskmanager.task.specification.TaskSpecifications;
import com.smarttaskmanager.user.entity.User;
import com.smarttaskmanager.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service for managing tasks scoped to the authenticated user.
 *
 * <p>Enforces ownership: every operation derives the user from the security context,
 * never from request parameters. Validates category ownership when creating or updating tasks.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;

    /** Maximum page size for task listing */
    private static final int MAX_PAGE_SIZE = 100;

    /** Allowed sort fields for task listing */
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "createdAt", "updatedAt", "title", "status", "priority", "dueDate"
    );

    /**
     * Creates a new task for the authenticated user.
     *
     * @param request task creation request
     * @return the created task response
     */
    @Transactional
    public TaskResponse createTask(CreateTaskRequest request) {
        User user = getCurrentUser();

        Task task = Task.builder()
                .user(user)
                .title(request.title().trim())
                .description(request.description())
                .status(request.status() != null ? request.status() : TaskStatus.TODO)
                .priority(request.priority() != null ? request.priority() : TaskPriority.MEDIUM)
                .dueDate(request.dueDate())
                .build();

        // Set category if provided, validating ownership
        if (request.categoryId() != null) {
            Category category = categoryRepository.findByIdAndUserId(request.categoryId(), user.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", "id", request.categoryId()));
            task.setCategory(category);
        }

        task = taskRepository.save(task);
        log.info("Task [{}] created for user [{}]", task.getTitle(), user.getEmail());
        return toResponse(task);
    }

    /**
     * Retrieves all tasks belonging to the authenticated user with filtering, pagination, and sorting.
     *
     * @param status optional status filter
     * @param priority optional priority filter
     * @param categoryId optional category filter
     * @param search optional search term for title/description
     * @param page page number (0-based)
     * @param size page size (max 100)
     * @param sortBy sort field (default: createdAt)
     * @param sortDirection sort direction (ASC or DESC, default: DESC)
     * @return paginated task responses
     */
    @Transactional(readOnly = true)
    public Page<TaskResponse> getUserTasks(
            TaskStatus status,
            TaskPriority priority,
            Long categoryId,
            String search,
            int page,
            int size,
            String sortBy,
            String sortDirection
    ) {
        User user = getCurrentUser();

        // Build specification with all filters
        Specification<Task> spec = TaskSpecifications.belongsToUser(user.getId());
        spec = spec.and(TaskSpecifications.hasStatus(status));
        spec = spec.and(TaskSpecifications.hasPriority(priority));
        spec = spec.and(TaskSpecifications.hasCategoryId(categoryId));
        spec = spec.and(TaskSpecifications.searchTitleOrDescription(search));

        // Validate and apply sorting
        String safeSortBy = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "createdAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDirection) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), Sort.by(direction, safeSortBy));

        Page<Task> tasks = taskRepository.findAll(spec, pageable);
        return tasks.map(this::toResponse);
    }

    /**
     * Retrieves a single task by ID, ensuring it belongs to the authenticated user.
     *
     * @param id task ID
     * @return the task response
     * @throws ResourceNotFoundException if the task does not exist or does not belong to the user
     */
    @Transactional(readOnly = true)
    public TaskResponse getTaskById(Long id) {
        User user = getCurrentUser();
        Task task = taskRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Task", "id", id));
        return toResponse(task);
    }

    /**
     * Updates an existing task belonging to the authenticated user.
     *
     * @param id task ID
     * @param request update request with optional fields
     * @return the updated task response
     */
    @Transactional
    public TaskResponse updateTask(Long id, UpdateTaskRequest request) {
        User user = getCurrentUser();
        Task task = taskRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Task", "id", id));

        if (request.title() != null) {
            task.setTitle(request.title().trim());
        }

        if (request.description() != null) {
            task.setDescription(request.description());
        }

        if (request.status() != null) {
            // Handle completion timestamp when status changes to COMPLETED
            if (request.status() == TaskStatus.COMPLETED && task.getStatus() != TaskStatus.COMPLETED) {
                task.setCompletedAt(Instant.now());
            }
            // Clear completion timestamp when task is reopened from COMPLETED
            else if (request.status() != TaskStatus.COMPLETED && task.getStatus() == TaskStatus.COMPLETED) {
                task.setCompletedAt(null);
            }
            task.setStatus(request.status());
        }

        if (request.priority() != null) {
            task.setPriority(request.priority());
        }

        if (request.dueDate() != null) {
            task.setDueDate(request.dueDate());
        }

        // Handle category change with ownership validation
        if (request.categoryId() != null) {
            Category category = categoryRepository.findByIdAndUserId(request.categoryId(), user.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", "id", request.categoryId()));
            task.setCategory(category);
        }
        // Note: categoryId == null in a partial update means "do not change the category".
        // To remove a category from a task, the client should use a dedicated endpoint or we could
        // add a boolean flag. For now, the category is preserved on partial updates.

        task = taskRepository.save(task);
        log.info("Task [{}] updated for user [{}]", task.getTitle(), user.getEmail());
        return toResponse(task);
    }

    /**
     * Deletes a task belonging to the authenticated user.
     *
     * @param id task ID
     */
    @Transactional
    public void deleteTask(Long id) {
        User user = getCurrentUser();
        Task task = taskRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Task", "id", id));

        taskRepository.delete(task);
        log.info("Task [{}] deleted for user [{}]", task.getTitle(), user.getEmail());
    }

    /**
     * Retrieves task statistics for the authenticated user.
     *
     * @return task statistics response
     */
    @Transactional(readOnly = true)
    public TaskStatisticsResponse getTaskStatistics() {
        User user = getCurrentUser();

        long totalTasks = taskRepository.countByUserId(user.getId());

        // Count by status
        Map<TaskStatus, Long> byStatus = new EnumMap<>(TaskStatus.class);
        for (TaskStatus s : TaskStatus.values()) {
            byStatus.put(s, taskRepository.countByUserIdAndStatus(user.getId(), s));
        }

        // Count by priority using projection
        Map<TaskPriority, Long> byPriority = new EnumMap<>(TaskPriority.class);
        for (PriorityCountProjection proj : taskRepository.countTasksByPriorityForUser(user.getId())) {
            byPriority.put(proj.getPriority(), proj.getCount());
        }

        long completedTasks = byStatus.getOrDefault(TaskStatus.COMPLETED, 0L);
        long pendingTasks = totalTasks - completedTasks;

        // Overdue: tasks with due date in the past and status not COMPLETED or CANCELLED
        List<TaskStatus> excludedStatuses = List.of(TaskStatus.COMPLETED, TaskStatus.CANCELLED);
        long overdueTasks = taskRepository.countByUserIdAndStatusNotInAndDueDateBefore(
                user.getId(), excludedStatuses, Instant.now());

        return new TaskStatisticsResponse(
                totalTasks,
                completedTasks,
                pendingTasks,
                overdueTasks,
                byStatus,
                byPriority
        );
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

    private TaskResponse toResponse(Task task) {
        TaskResponse.CategoryInfo categoryInfo = null;
        if (task.getCategory() != null) {
            Category cat = task.getCategory();
            categoryInfo = new TaskResponse.CategoryInfo(cat.getId(), cat.getName());
        }
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getPriority(),
                task.getDueDate(),
                task.getCompletedAt(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                categoryInfo
        );
    }
}
