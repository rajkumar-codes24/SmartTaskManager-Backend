package com.smarttaskmanager.task.controller;

import com.smarttaskmanager.common.dto.ApiResponse;
import com.smarttaskmanager.task.dto.CreateLabelRequest;
import com.smarttaskmanager.task.dto.LabelResponse;
import com.smarttaskmanager.task.dto.UpdateLabelRequest;
import com.smarttaskmanager.task.service.LabelService;
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
 * REST controller for managing labels and task-label assignments.
 */
@RestController
@RequestMapping("/api/v1/labels")
@RequiredArgsConstructor
@Tag(name = "Label Management", description = "CRUD operations for task labels")
@SecurityRequirement(name = "bearerAuth")
public class LabelController {

    private final LabelService labelService;

    @PostMapping
    @Operation(summary = "Create a label", description = "Creates a new label for the authenticated user.")
    public ResponseEntity<ApiResponse<LabelResponse>> createLabel(
            @Valid @RequestBody CreateLabelRequest request) {
        LabelResponse response = labelService.createLabel(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Label created successfully.", response));
    }

    @GetMapping
    @Operation(summary = "List user labels", description = "Returns all labels belonging to the authenticated user.")
    public ResponseEntity<ApiResponse<List<LabelResponse>>> getUserLabels() {
        List<LabelResponse> labels = labelService.getUserLabels();
        return ResponseEntity.ok(ApiResponse.success("Labels retrieved successfully.", labels));
    }

    @GetMapping("/{labelId}")
    @Operation(summary = "Get label by ID", description = "Returns a single label by ID.")
    public ResponseEntity<ApiResponse<LabelResponse>> getLabelById(@PathVariable Long labelId) {
        LabelResponse response = labelService.getLabelById(labelId);
        return ResponseEntity.ok(ApiResponse.success("Label retrieved successfully.", response));
    }

    @PutMapping("/{labelId}")
    @Operation(summary = "Update a label", description = "Updates an existing label. Only provided fields will be updated.")
    public ResponseEntity<ApiResponse<LabelResponse>> updateLabel(
            @PathVariable Long labelId,
            @Valid @RequestBody UpdateLabelRequest request) {
        LabelResponse response = labelService.updateLabel(labelId, request);
        return ResponseEntity.ok(ApiResponse.success("Label updated successfully.", response));
    }

    @DeleteMapping("/{labelId}")
    @Operation(summary = "Delete a label", description = "Deletes a label.")
    public ResponseEntity<ApiResponse<Void>> deleteLabel(@PathVariable Long labelId) {
        labelService.deleteLabel(labelId);
        return ResponseEntity.ok(ApiResponse.success("Label deleted successfully."));
    }

    @PostMapping("/tasks/{taskId}/labels/{labelId}")
    @Operation(summary = "Assign label to task", description = "Assigns a label to a task.")
    public ResponseEntity<ApiResponse<Void>> assignLabelToTask(
            @PathVariable Long taskId,
            @PathVariable Long labelId) {
        labelService.assignLabelToTask(taskId, labelId);
        return ResponseEntity.ok(ApiResponse.success("Label assigned to task successfully."));
    }

    @DeleteMapping("/tasks/{taskId}/labels/{labelId}")
    @Operation(summary = "Remove label from task", description = "Removes a label from a task.")
    public ResponseEntity<ApiResponse<Void>> removeLabelFromTask(
            @PathVariable Long taskId,
            @PathVariable Long labelId) {
        labelService.removeLabelFromTask(taskId, labelId);
        return ResponseEntity.ok(ApiResponse.success("Label removed from task successfully."));
    }

    @GetMapping("/tasks/{taskId}")
    @Operation(summary = "Get labels by task", description = "Returns all labels assigned to a task.")
    public ResponseEntity<ApiResponse<List<LabelResponse>>> getLabelsByTaskId(@PathVariable Long taskId) {
        List<LabelResponse> labels = labelService.getLabelsByTaskId(taskId);
        return ResponseEntity.ok(ApiResponse.success("Labels retrieved successfully.", labels));
    }
}
