package com.smarttaskmanager.task.controller;

import com.smarttaskmanager.common.dto.ApiResponse;
import com.smarttaskmanager.task.dto.CreateRecurrenceRequest;
import com.smarttaskmanager.task.dto.RecurrenceResponse;
import com.smarttaskmanager.task.service.TaskRecurrenceService;
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

/**
 * REST controller for managing task recurrence rules.
 */
@RestController
@RequestMapping("/api/v1/tasks/{taskId}/recurrence")
@RequiredArgsConstructor
@Tag(name = "Task Recurrence", description = "Manage recurring task rules")
@SecurityRequirement(name = "bearerAuth")
public class TaskRecurrenceController {

    private final TaskRecurrenceService recurrenceService;

    @PostMapping
    @Operation(summary = "Create recurrence rule", description = "Creates a recurrence rule for the specified task.")
    public ResponseEntity<ApiResponse<RecurrenceResponse>> createRecurrence(
            @PathVariable Long taskId,
            @Valid @RequestBody CreateRecurrenceRequest request) {
        RecurrenceResponse response = recurrenceService.createRecurrence(taskId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Recurrence rule created successfully.", response));
    }

    @GetMapping
    @Operation(summary = "Get recurrence rule", description = "Returns the recurrence rule for the specified task.")
    public ResponseEntity<ApiResponse<RecurrenceResponse>> getRecurrence(@PathVariable Long taskId) {
        RecurrenceResponse response = recurrenceService.getRecurrence(taskId);
        return ResponseEntity.ok(ApiResponse.success("Recurrence rule retrieved successfully.", response));
    }

    @PutMapping
    @Operation(summary = "Update recurrence rule", description = "Updates the recurrence rule for the specified task.")
    public ResponseEntity<ApiResponse<RecurrenceResponse>> updateRecurrence(
            @PathVariable Long taskId,
            @Valid @RequestBody CreateRecurrenceRequest request) {
        RecurrenceResponse response = recurrenceService.updateRecurrence(taskId, request);
        return ResponseEntity.ok(ApiResponse.success("Recurrence rule updated successfully.", response));
    }

    @DeleteMapping
    @Operation(summary = "Delete recurrence rule", description = "Deletes the recurrence rule for the specified task.")
    public ResponseEntity<ApiResponse<Void>> deleteRecurrence(@PathVariable Long taskId) {
        recurrenceService.deleteRecurrence(taskId);
        return ResponseEntity.ok(ApiResponse.success("Recurrence rule deleted successfully."));
    }

    @PostMapping("/pause")
    @Operation(summary = "Pause recurrence", description = "Pauses the recurrence rule for the specified task.")
    public ResponseEntity<ApiResponse<RecurrenceResponse>> pauseRecurrence(@PathVariable Long taskId) {
        RecurrenceResponse response = recurrenceService.pauseRecurrence(taskId);
        return ResponseEntity.ok(ApiResponse.success("Recurrence rule paused successfully.", response));
    }

    @PostMapping("/resume")
    @Operation(summary = "Resume recurrence", description = "Resumes the recurrence rule for the specified task.")
    public ResponseEntity<ApiResponse<RecurrenceResponse>> resumeRecurrence(@PathVariable Long taskId) {
        RecurrenceResponse response = recurrenceService.resumeRecurrence(taskId);
        return ResponseEntity.ok(ApiResponse.success("Recurrence rule resumed successfully.", response));
    }
}
