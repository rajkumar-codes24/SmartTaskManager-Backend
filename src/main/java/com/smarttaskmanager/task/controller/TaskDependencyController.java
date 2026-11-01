package com.smarttaskmanager.task.controller;

import com.smarttaskmanager.common.dto.ApiResponse;
import com.smarttaskmanager.task.dto.DependencyStatusResponse;
import com.smarttaskmanager.task.entity.Task;
import com.smarttaskmanager.task.service.TaskDependencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for managing task dependencies.
 */
@RestController
@RequestMapping("/api/v1/tasks/{taskId}/dependencies")
@RequiredArgsConstructor
@Tag(name = "Task Dependencies", description = "Manage task execution order dependencies")
@SecurityRequirement(name = "bearerAuth")
public class TaskDependencyController {

    private final TaskDependencyService dependencyService;

    @PostMapping("/{dependencyTaskId}")
    @Operation(summary = "Add dependency", description = "Makes the specified task depend on another task.")
    public ResponseEntity<ApiResponse<Void>> addDependency(
            @PathVariable Long taskId,
            @PathVariable Long dependencyTaskId) {
        dependencyService.addDependency(taskId, dependencyTaskId);
        return ResponseEntity.ok(ApiResponse.success("Dependency added successfully."));
    }

    @DeleteMapping("/{dependencyTaskId}")
    @Operation(summary = "Remove dependency", description = "Removes a dependency relationship.")
    public ResponseEntity<ApiResponse<Void>> removeDependency(
            @PathVariable Long taskId,
            @PathVariable Long dependencyTaskId) {
        dependencyService.removeDependency(taskId, dependencyTaskId);
        return ResponseEntity.ok(ApiResponse.success("Dependency removed successfully."));
    }

    @GetMapping
    @Operation(summary = "List dependencies", description = "Returns tasks that this task depends on.")
    public ResponseEntity<ApiResponse<List<Task>>> getDependencies(@PathVariable Long taskId) {
        List<Task> dependencies = dependencyService.getDependencies(taskId);
        return ResponseEntity.ok(ApiResponse.success("Dependencies retrieved successfully.", dependencies));
    }

    @GetMapping("/dependents")
    @Operation(summary = "List dependents", description = "Returns tasks that depend on this task.")
    public ResponseEntity<ApiResponse<List<Task>>> getDependents(@PathVariable Long taskId) {
        List<Task> dependents = dependencyService.getDependents(taskId);
        return ResponseEntity.ok(ApiResponse.success("Dependents retrieved successfully.", dependents));
    }

    @GetMapping("/status")
    @Operation(summary = "Get dependency status", description = "Returns completion status of all dependencies for a task.")
    public ResponseEntity<ApiResponse<DependencyStatusResponse>> getDependencyStatus(@PathVariable Long taskId) {
        DependencyStatusResponse status = dependencyService.getDependencyStatus(taskId);
        return ResponseEntity.ok(ApiResponse.success("Dependency status retrieved successfully.", status));
    }
}
