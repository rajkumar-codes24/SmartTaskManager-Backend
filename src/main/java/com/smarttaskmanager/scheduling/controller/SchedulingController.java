package com.smarttaskmanager.scheduling.controller;

import com.smarttaskmanager.common.dto.ApiResponse;
import com.smarttaskmanager.scheduling.dto.DeadlineRisk;
import com.smarttaskmanager.scheduling.dto.SchedulingConflict;
import com.smarttaskmanager.scheduling.dto.SchedulingPlanResponse;
import com.smarttaskmanager.scheduling.dto.SchedulingPreferencesResponse;
import com.smarttaskmanager.scheduling.dto.UpdateSchedulingPreferencesRequest;
import com.smarttaskmanager.scheduling.service.SchedulingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller for smart scheduling engine operations.
 * All endpoints require JWT authentication.
 */
@RestController
@RequestMapping("/api/v1/scheduling")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Scheduling", description = "Smart Scheduling Engine APIs")
public class SchedulingController {

    private final SchedulingService schedulingService;

    @GetMapping("/preferences")
    @Operation(summary = "Get scheduling preferences", description = "Returns the authenticated user's scheduling preferences. Creates defaults if none exist.")
    public ResponseEntity<ApiResponse<SchedulingPreferencesResponse>> getPreferences() {
        SchedulingPreferencesResponse response = schedulingService.getPreferences();
        return ResponseEntity.ok(ApiResponse.success("Scheduling preferences retrieved.", response));
    }

    @PutMapping("/preferences")
    @Operation(summary = "Update scheduling preferences", description = "Updates the authenticated user's working hours, timezone, and scheduling defaults.")
    public ResponseEntity<ApiResponse<SchedulingPreferencesResponse>> updatePreferences(
            @Valid @RequestBody UpdateSchedulingPreferencesRequest request) {
        SchedulingPreferencesResponse response = schedulingService.updatePreferences(request);
        return ResponseEntity.ok(ApiResponse.success("Scheduling preferences updated.", response));
    }

    @PostMapping("/plan")
    @Operation(summary = "Generate schedule plan", description = "Generates a proposed schedule based on priorities, deadlines, dependencies, and working hours. Does not modify the database.")
    public ResponseEntity<ApiResponse<SchedulingPlanResponse>> generatePlan() {
        SchedulingPlanResponse response = schedulingService.generatePlan();
        return ResponseEntity.ok(ApiResponse.success("Schedule plan generated.", response));
    }

    @GetMapping("/plan")
    @Operation(summary = "Get current schedule plan", description = "Returns the current proposed schedule plan.")
    public ResponseEntity<ApiResponse<SchedulingPlanResponse>> getCurrentPlan() {
        SchedulingPlanResponse response = schedulingService.getCurrentPlan();
        return ResponseEntity.ok(ApiResponse.success("Current schedule plan retrieved.", response));
    }

    @PostMapping("/plan/apply")
    @Operation(summary = "Apply schedule plan", description = "Applies the proposed schedule to tasks, updating scheduled times and status. Requires explicit user action.")
    public ResponseEntity<ApiResponse<SchedulingPlanResponse>> applyPlan() {
        SchedulingPlanResponse response = schedulingService.applyPlan();
        return ResponseEntity.ok(ApiResponse.success("Schedule plan applied.", response));
    }

    @GetMapping("/conflicts")
    @Operation(summary = "Get scheduling conflicts", description = "Returns overlapping scheduled tasks for the authenticated user.")
    public ResponseEntity<ApiResponse<List<SchedulingConflict>>> getConflicts() {
        List<SchedulingConflict> conflicts = schedulingService.getConflicts();
        return ResponseEntity.ok(ApiResponse.success("Scheduling conflicts retrieved.", conflicts));
    }

    @GetMapping("/deadline-risks")
    @Operation(summary = "Get deadline risks", description = "Returns tasks at risk of missing their deadlines.")
    public ResponseEntity<ApiResponse<List<DeadlineRisk>>> getDeadlineRisks() {
        List<DeadlineRisk> risks = schedulingService.getDeadlineRisks();
        return ResponseEntity.ok(ApiResponse.success("Deadline risks retrieved.", risks));
    }
}
