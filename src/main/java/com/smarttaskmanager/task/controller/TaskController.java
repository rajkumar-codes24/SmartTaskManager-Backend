package com.smarttaskmanager.task.controller;

import com.smarttaskmanager.task.dto.CreateTaskRequest;
import com.smarttaskmanager.task.dto.TaskResponse;
import com.smarttaskmanager.task.dto.TaskStatisticsResponse;
import com.smarttaskmanager.task.dto.UpdateTaskRequest;
import com.smarttaskmanager.task.entity.TaskPriority;
import com.smarttaskmanager.task.entity.TaskStatus;
import com.smarttaskmanager.task.service.TaskService;
import com.smarttaskmanager.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST controller for managing user-specific tasks.
 *
 * <p>All endpoints are protected and scoped to the authenticated user.
 * Users can only access their own tasks.</p>
 */
@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
@Tag(name = "Task Management", description = "CRUD operations for user tasks")
@SecurityRequirement(name = "bearerAuth")
public class TaskController {

    private final TaskService taskService;

    @PostMapping
    @Operation(summary = "Create a task", description = "Creates a new task for the authenticated user.")
    public ResponseEntity<ApiResponse<TaskResponse>> createTask(
            @Valid @RequestBody CreateTaskRequest request) {
        TaskResponse response = taskService.createTask(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Task created successfully.", response));
    }

    @GetMapping
    @Operation(summary = "List user tasks", description = "Returns paginated tasks for the authenticated user with optional filtering, searching, and sorting.")
    public ResponseEntity<ApiResponse<Page<TaskResponse>>> getUserTasks(
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) TaskPriority priority,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection
    ) {
        Page<TaskResponse> tasks = taskService.getUserTasks(
                status, priority, categoryId, search, page, size, sortBy, sortDirection);
        return ResponseEntity.ok(ApiResponse.success("Tasks retrieved successfully.", tasks));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get task by ID", description = "Returns a single task by ID if it belongs to the authenticated user.")
    public ResponseEntity<ApiResponse<TaskResponse>> getTaskById(@PathVariable Long id) {
        TaskResponse response = taskService.getTaskById(id);
        return ResponseEntity.ok(ApiResponse.success("Task retrieved successfully.", response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a task", description = "Updates an existing task belonging to the authenticated user. Only provided fields will be updated.")
    public ResponseEntity<ApiResponse<TaskResponse>> updateTask(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTaskRequest request) {
        TaskResponse response = taskService.updateTask(id, request);
        return ResponseEntity.ok(ApiResponse.success("Task updated successfully.", response));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a task", description = "Deletes a task belonging to the authenticated user.")
    public ResponseEntity<ApiResponse<Void>> deleteTask(@PathVariable Long id) {
        taskService.deleteTask(id);
        return ResponseEntity.ok(ApiResponse.success("Task deleted successfully."));
    }

    @GetMapping("/statistics")
    @Operation(summary = "Get task statistics", description = "Returns task statistics for the authenticated user, including counts by status and priority.")
    public ResponseEntity<ApiResponse<TaskStatisticsResponse>> getTaskStatistics() {
        TaskStatisticsResponse stats = taskService.getTaskStatistics();
        return ResponseEntity.ok(ApiResponse.success("Task statistics retrieved successfully.", stats));
    }
}
