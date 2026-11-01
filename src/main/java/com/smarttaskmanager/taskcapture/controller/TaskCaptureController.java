package com.smarttaskmanager.taskcapture.controller;

import com.smarttaskmanager.common.dto.ApiResponse;
import com.smarttaskmanager.task.dto.TaskResponse;
import com.smarttaskmanager.taskcapture.dto.TaskCaptureConfirmRequest;
import com.smarttaskmanager.taskcapture.dto.TaskCaptureParseResponse;
import com.smarttaskmanager.taskcapture.dto.TaskCaptureRequest;
import com.smarttaskmanager.taskcapture.service.TaskCaptureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for AI-powered task capture.
 * Allows users to create tasks from natural language input.
 * All endpoints require JWT authentication.
 */
@RestController
@RequestMapping("/api/v1/task-capture")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Task Capture", description = "AI-Powered Task Capture APIs")
public class TaskCaptureController {

    private final TaskCaptureService taskCaptureService;

    @PostMapping("/parse")
    @Operation(summary = "Parse natural language into task preview",
            description = "Parses natural language input into structured task data. "
                    + "Returns a preview for user confirmation. No task is created yet.")
    public ResponseEntity<ApiResponse<TaskCaptureParseResponse>> parse(
            @Valid @RequestBody TaskCaptureRequest request) {
        TaskCaptureParseResponse response = taskCaptureService.parse(request.text());
        return ResponseEntity.ok(ApiResponse.success("Task parsed successfully.", response));
    }

    @PostMapping("/confirm")
    @Operation(summary = "Confirm and create parsed task",
            description = "Creates a task from the confirmed parsed data. "
                    + "Optionally creates a category and recurrence rule.")
    public ResponseEntity<ApiResponse<TaskResponse>> confirm(
            @Valid @RequestBody TaskCaptureConfirmRequest request) {
        TaskResponse response = taskCaptureService.confirm(request);
        return ResponseEntity.ok(ApiResponse.success("Task created successfully.", response));
    }
}
