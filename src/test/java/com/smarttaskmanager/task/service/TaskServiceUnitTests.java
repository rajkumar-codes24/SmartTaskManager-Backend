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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceUnitTests {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private TaskService taskService;

    private User user;
    private Category category;
    private Task task;

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
                .description("Work tasks")
                .color("#5469D4")
                .build();

        task = Task.builder()
                .id(1L)
                .user(user)
                .category(category)
                .title("Complete project")
                .description("Finish the SmartTaskManager project")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.HIGH)
                .dueDate(Instant.parse("2027-12-31T23:59:59Z"))
                .completedAt(null)
                .build();

        // Set up security context
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(user.getEmail(), null);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // ==================== Create Task Tests ====================

    @Test
    @DisplayName("Create task succeeds with valid input")
    void createTask_validInput_returnsResponse() {
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));

        Task savedTask = Task.builder()
                .id(1L)
                .user(user)
                .title("Complete project")
                .description("Finish the project")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.HIGH)
                .dueDate(Instant.parse("2027-12-31T23:59:59Z"))
                .build();
        when(taskRepository.save(any(Task.class))).thenReturn(savedTask);

        CreateTaskRequest request = new CreateTaskRequest(
                "Complete project", "Finish the project", TaskStatus.TODO, TaskPriority.HIGH,
                Instant.parse("2027-12-31T23:59:59Z"), null);

        TaskResponse response = taskService.createTask(request);

        assertThat(response.id()).isNotNull();
        assertThat(response.title()).isEqualTo("Complete project");
        assertThat(response.status()).isEqualTo(TaskStatus.TODO);
        assertThat(response.priority()).isEqualTo(TaskPriority.HIGH);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        Task saved = captor.getValue();
        assertThat(saved.getUser().getId()).isEqualTo(user.getId());
    }

    @Test
    @DisplayName("Create task with category validates ownership")
    void createTask_withCategory_validatesOwnership() {
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(categoryRepository.findByIdAndUserId(1L, user.getId())).thenReturn(Optional.of(category));

        Task savedTask = Task.builder()
                .id(1L)
                .user(user)
                .category(category)
                .title("Categorized task")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.MEDIUM)
                .build();
        when(taskRepository.save(any(Task.class))).thenReturn(savedTask);

        CreateTaskRequest request = new CreateTaskRequest(
                "Categorized task", null, null, null, null, 1L);

        TaskResponse response = taskService.createTask(request);

        assertThat(response.title()).isEqualTo("Categorized task");
        assertThat(response.category()).isNotNull();
        assertThat(response.category().id()).isEqualTo(1L);
        verify(categoryRepository).findByIdAndUserId(1L, user.getId());
    }

    @Test
    @DisplayName("Create task with other user's category throws exception")
    void createTask_withOtherUserCategory_throwsException() {
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(categoryRepository.findByIdAndUserId(999L, user.getId())).thenReturn(Optional.empty());

        CreateTaskRequest request = new CreateTaskRequest(
                "Task", null, null, null, null, 999L);

        assertThatThrownBy(() -> taskService.createTask(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Create task defaults status to TODO and priority to MEDIUM")
    void createTask_defaultsStatusAndPriority() {
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));

        Task savedTask = Task.builder()
                .id(1L)
                .user(user)
                .title("Simple task")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.MEDIUM)
                .build();
        when(taskRepository.save(any(Task.class))).thenReturn(savedTask);

        CreateTaskRequest request = new CreateTaskRequest("Simple task", null, null, null, null, null);

        TaskResponse response = taskService.createTask(request);

        assertThat(response.status()).isEqualTo(TaskStatus.TODO);
        assertThat(response.priority()).isEqualTo(TaskPriority.MEDIUM);
    }

    // ==================== Get Task Tests ====================

    @Test
    @DisplayName("Get task by ID returns task")
    void getTaskById_existingTask_returnsResponse() {
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(taskRepository.findByIdAndUserId(1L, user.getId())).thenReturn(Optional.of(task));

        TaskResponse response = taskService.getTaskById(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.title()).isEqualTo("Complete project");
    }

    @Test
    @DisplayName("Get task by ID throws when task not found")
    void getTaskById_nonExistent_throwsException() {
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(taskRepository.findByIdAndUserId(999L, user.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.getTaskById(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Get task by ID throws when task belongs to other user")
    void getTaskById_otherUserTask_throwsException() {
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(taskRepository.findByIdAndUserId(1L, user.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.getTaskById(1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ==================== Update Task Tests ====================

    @Test
    @DisplayName("Update task with partial fields")
    void updateTask_partialUpdate_updatesOnlyProvidedFields() {
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(taskRepository.findByIdAndUserId(1L, user.getId())).thenReturn(Optional.of(task));

        Task updatedTask = Task.builder()
                .id(1L)
                .user(user)
                .category(category)
                .title("Updated title")
                .description("Finish the SmartTaskManager project")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.HIGH)
                .dueDate(Instant.parse("2027-12-31T23:59:59Z"))
                .build();
        when(taskRepository.save(any(Task.class))).thenReturn(updatedTask);

        UpdateTaskRequest request = new UpdateTaskRequest("Updated title", null, null, null, null, null);

        TaskResponse response = taskService.updateTask(1L, request);

        assertThat(response.title()).isEqualTo("Updated title");
    }

    @Test
    @DisplayName("Update task to COMPLETED sets completedAt")
    void updateTask_toCompleted_setsCompletedAt() {
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(taskRepository.findByIdAndUserId(1L, user.getId())).thenReturn(Optional.of(task));

        Task updatedTask = Task.builder()
                .id(1L)
                .user(user)
                .title("Complete project")
                .status(TaskStatus.COMPLETED)
                .priority(TaskPriority.HIGH)
                .completedAt(Instant.now())
                .build();
        when(taskRepository.save(any(Task.class))).thenReturn(updatedTask);

        UpdateTaskRequest request = new UpdateTaskRequest(null, null, TaskStatus.COMPLETED, null, null, null);

        TaskResponse response = taskService.updateTask(1L, request);

        assertThat(response.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(response.completedAt()).isNotNull();
    }

    @Test
    @DisplayName("Update task from COMPLETED to TODO clears completedAt")
    void updateTask_fromCompletedToTodo_clearsCompletedAt() {
        Task completedTask = Task.builder()
                .id(1L)
                .user(user)
                .title("Complete project")
                .status(TaskStatus.COMPLETED)
                .priority(TaskPriority.HIGH)
                .completedAt(Instant.now())
                .build();

        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(taskRepository.findByIdAndUserId(1L, user.getId())).thenReturn(Optional.of(completedTask));

        Task reopenedTask = Task.builder()
                .id(1L)
                .user(user)
                .title("Complete project")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.HIGH)
                .completedAt(null)
                .build();
        when(taskRepository.save(any(Task.class))).thenReturn(reopenedTask);

        UpdateTaskRequest request = new UpdateTaskRequest(null, null, TaskStatus.TODO, null, null, null);

        TaskResponse response = taskService.updateTask(1L, request);

        assertThat(response.status()).isEqualTo(TaskStatus.TODO);
        assertThat(response.completedAt()).isNull();
    }

    @Test
    @DisplayName("Update task category validates ownership")
    void updateTask_categoryChange_validatesOwnership() {
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(taskRepository.findByIdAndUserId(1L, user.getId())).thenReturn(Optional.of(task));
        when(categoryRepository.findByIdAndUserId(2L, user.getId())).thenReturn(Optional.of(category));

        Task updatedTask = Task.builder()
                .id(1L)
                .user(user)
                .category(category)
                .title("Complete project")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.HIGH)
                .build();
        when(taskRepository.save(any(Task.class))).thenReturn(updatedTask);

        UpdateTaskRequest request = new UpdateTaskRequest(null, null, null, null, null, 2L);

        taskService.updateTask(1L, request);

        verify(categoryRepository).findByIdAndUserId(2L, user.getId());
    }

    // ==================== Delete Task Tests ====================

    @Test
    @DisplayName("Delete task succeeds")
    void deleteTask_existingTask_succeeds() {
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(taskRepository.findByIdAndUserId(1L, user.getId())).thenReturn(Optional.of(task));

        taskService.deleteTask(1L);

        verify(taskRepository).delete(task);
    }

    @Test
    @DisplayName("Delete non-existent task throws exception")
    void deleteTask_nonExistent_throwsException() {
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(taskRepository.findByIdAndUserId(999L, user.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.deleteTask(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ==================== List Tasks Tests ====================

    @Test
    @DisplayName("List tasks returns paginated results")
    void getUserTasks_returnsPaginatedResults() {
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(taskRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(task)));

        Page<TaskResponse> result = taskService.getUserTasks(
                null, null, null, null, 0, 20, "createdAt", "DESC");

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).title()).isEqualTo("Complete project");
    }

    @Test
    @DisplayName("List tasks with status filter")
    void getUserTasks_withStatusFilter_appliesFilter() {
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(taskRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(task)));

        Page<TaskResponse> result = taskService.getUserTasks(
                TaskStatus.TODO, null, null, null, 0, 20, "createdAt", "DESC");

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("List tasks with search term")
    void getUserTasks_withSearchTerm_appliesFilter() {
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(taskRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(task)));

        Page<TaskResponse> result = taskService.getUserTasks(
                null, null, null, "project", 0, 20, "createdAt", "DESC");

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("List tasks enforces max page size")
    void getUserTasks_largePageSize_enforcesMax() {
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(taskRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(task)));

        Page<TaskResponse> result = taskService.getUserTasks(
                null, null, null, null, 0, 9999, "createdAt", "DESC");

        // Should cap at MAX_PAGE_SIZE (100)
        verify(taskRepository).findAll(any(Specification.class), any(Pageable.class));
        assertThat(result.getSize()).isLessThanOrEqualTo(100);
    }

    @Test
    @DisplayName("List tasks with invalid sort field defaults to createdAt")
    void getUserTasks_invalidSortField_defaultsToCreatedAt() {
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(taskRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(task)));

        Page<TaskResponse> result = taskService.getUserTasks(
                null, null, null, null, 0, 20, "invalidField", "DESC");

        assertThat(result.getContent()).hasSize(1);
    }

    // ==================== Statistics Tests ====================

    @Test
    @DisplayName("Get statistics returns correct counts")
    void getTaskStatistics_returnsCounts() {
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(taskRepository.countByUserId(user.getId())).thenReturn(5L);
        when(taskRepository.countByUserIdAndStatus(user.getId(), TaskStatus.TODO)).thenReturn(2L);
        when(taskRepository.countByUserIdAndStatus(user.getId(), TaskStatus.IN_PROGRESS)).thenReturn(1L);
        when(taskRepository.countByUserIdAndStatus(user.getId(), TaskStatus.COMPLETED)).thenReturn(2L);
        when(taskRepository.countByUserIdAndStatus(user.getId(), TaskStatus.CANCELLED)).thenReturn(0L);
        when(taskRepository.countTasksByPriorityForUser(user.getId())).thenReturn(List.of());
        when(taskRepository.countByUserIdAndStatusNotInAndDueDateBefore(anyLong(), any(List.class), any(Instant.class))).thenReturn(0L);

        TaskStatisticsResponse stats = taskService.getTaskStatistics();

        assertThat(stats.totalTasks()).isEqualTo(5L);
        assertThat(stats.completedTasks()).isEqualTo(2L);
        assertThat(stats.pendingTasks()).isEqualTo(3L);
    }

    @Test
    @DisplayName("Get statistics with no tasks returns zeros")
    void getTaskStatistics_noTasks_returnsZeros() {
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(taskRepository.countByUserId(user.getId())).thenReturn(0L);
        when(taskRepository.countByUserIdAndStatus(anyLong(), any(TaskStatus.class))).thenReturn(0L);
        when(taskRepository.countTasksByPriorityForUser(user.getId())).thenReturn(List.of());
        when(taskRepository.countByUserIdAndStatusNotInAndDueDateBefore(anyLong(), any(List.class), any(Instant.class))).thenReturn(0L);

        TaskStatisticsResponse stats = taskService.getTaskStatistics();

        assertThat(stats.totalTasks()).isEqualTo(0L);
        assertThat(stats.completedTasks()).isEqualTo(0L);
        assertThat(stats.pendingTasks()).isEqualTo(0L);
        assertThat(stats.overdueTasks()).isEqualTo(0L);
    }
}
