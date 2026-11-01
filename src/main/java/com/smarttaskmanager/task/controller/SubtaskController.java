package com.smarttaskmanager.task.controller;

import com.smarttaskmanager.common.dto.ApiResponse;
import com.smarttaskmanager.task.dto.CreateSubtaskRequest;
import com.smarttaskmanager.task.dto.SubtaskResponse;
import com.smarttaskmanager.task.dto.UpdateSubtaskRequest;
import com.smarttaskmanager.task.service.SubtaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for managing subtasks under a parent task.
 */
@RestController
@RequestMapping("/api/v1/tasks/{taskId}/subtasks")
@RequiredArgsConstructor
@Tag(name = "Subtask Management", description = "CRUD operations for task subtasks")
@SecurityRequirement(name = "bearerAuth")
public class SubtaskController {

    private final SubtaskService subtaskService;

    @PostMapping
    @Operation(summary = "Create a subtask", description = "Creates a new subtask under the specified parent task.")
    public ResponseEntity<ApiResponse<SubtaskResponse>> createSubtask(
            @PathVariable Long taskId,
            @Valid @RequestBody CreateSubtaskRequest request) {
        SubtaskResponse response = subtaskService.createSubtask(taskId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Subtask created successfully.", response));
    }

    @GetMapping
    @Operation(summary = "List subtasks", description = "Returns all subtasks for the specified parent task.")
    public ResponseEntity<ApiResponse<List<SubtaskResponse>>> getSubtasks(@PathVariable Long taskId) {
        List<SubtaskResponse> subtasks = subtaskService.getSubtasksByTaskId(taskId);
        return ResponseEntity.ok(ApiResponse.success("Subtasks retrieved successfully.", subtasks));
    }

    @GetMapping("/{subtaskId}")
    @Operation(summary = "Get subtask by ID", description = "Returns a single subtask by ID.")
    public ResponseEntity<ApiResponse<SubtaskResponse>> getSubtaskById(
            @PathVariable Long taskId,
            @PathVariable Long subtaskId) {
        SubtaskResponse response = subtaskService.getSubtaskById(taskId, subtaskId);
        return ResponseEntity.ok(ApiResponse.success("Subtask retrieved successfully.", response));
    }

    @PutMapping("/{subtaskId}")
    @Operation(summary = "Update a subtask", description = "Updates an existing subtask. Only provided fields will be updated.")
    public ResponseEntity<ApiResponse<SubtaskResponse>> updateSubtask(
            @PathVariable Long taskId,
            @PathVariable Long subtaskId,
            @Valid @RequestBody UpdateSubtaskRequest request) {
        SubtaskResponse response = subtaskService.updateSubtask(taskId, subtaskId, request);
        return ResponseEntity.ok(ApiResponse.success("Subtask updated successfully.", response));
    }

    @DeleteMapping("/{subtaskId}")
    @Operation(summary = "Delete a subtask", description = "Deletes a subtask.")
    public ResponseEntity<ApiResponse<Void>> deleteSubtask(
            @PathVariable Long taskId,
            @PathVariable Long subtaskId) {
        subtaskService.deleteSubtask(taskId, subtaskId);
        return ResponseEntity.ok(ApiResponse.success("Subtask deleted successfully."));
    }
}
