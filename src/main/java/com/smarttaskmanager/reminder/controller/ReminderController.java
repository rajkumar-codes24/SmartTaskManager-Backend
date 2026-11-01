package com.smarttaskmanager.reminder.controller;

import com.smarttaskmanager.common.dto.ApiResponse;
import com.smarttaskmanager.reminder.dto.CreateReminderRequest;
import com.smarttaskmanager.reminder.dto.ReminderResponse;
import com.smarttaskmanager.reminder.dto.UpdateReminderRequest;
import com.smarttaskmanager.reminder.service.ReminderService;
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
 * REST controller for managing user-specific reminders.
 *
 * <p>All endpoints are protected and scoped to the authenticated user.
 * Users can only access their own reminders.</p>
 */
@RestController
@RequestMapping("/api/v1/reminders")
@RequiredArgsConstructor
@Tag(name = "Reminder Management", description = "CRUD operations for user reminders")
@SecurityRequirement(name = "bearerAuth")
public class ReminderController {

    private final ReminderService reminderService;

    @PostMapping
    @Operation(summary = "Create a reminder", description = "Creates a new reminder for the authenticated user's task.")
    public ResponseEntity<ApiResponse<ReminderResponse>> createReminder(
            @Valid @RequestBody CreateReminderRequest request) {
        ReminderResponse response = reminderService.createReminder(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Reminder created successfully.", response));
    }

    @GetMapping
    @Operation(summary = "List user reminders", description = "Returns all reminders for the authenticated user.")
    public ResponseEntity<ApiResponse<List<ReminderResponse>>> getUserReminders() {
        List<ReminderResponse> reminders = reminderService.getUserReminders();
        return ResponseEntity.ok(ApiResponse.success("Reminders retrieved successfully.", reminders));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get reminder by ID", description = "Returns a single reminder by ID if it belongs to the authenticated user.")
    public ResponseEntity<ApiResponse<ReminderResponse>> getReminderById(@PathVariable Long id) {
        ReminderResponse response = reminderService.getReminderById(id);
        return ResponseEntity.ok(ApiResponse.success("Reminder retrieved successfully.", response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a reminder", description = "Updates an existing reminder belonging to the authenticated user.")
    public ResponseEntity<ApiResponse<ReminderResponse>> updateReminder(
            @PathVariable Long id,
            @Valid @RequestBody UpdateReminderRequest request) {
        ReminderResponse response = reminderService.updateReminder(id, request);
        return ResponseEntity.ok(ApiResponse.success("Reminder updated successfully.", response));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a reminder", description = "Deletes a reminder belonging to the authenticated user.")
    public ResponseEntity<ApiResponse<Void>> deleteReminder(@PathVariable Long id) {
        reminderService.deleteReminder(id);
        return ResponseEntity.ok(ApiResponse.success("Reminder deleted successfully."));
    }
}
