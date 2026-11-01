package com.smarttaskmanager.analytics.controller;

import com.smarttaskmanager.analytics.dto.*;
import com.smarttaskmanager.analytics.service.AnalyticsService;
import com.smarttaskmanager.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Controller for productivity analytics and goal management.
 * All endpoints require JWT authentication.
 */
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Analytics", description = "Productivity Intelligence APIs")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/overview")
    @Operation(summary = "Get dashboard overview", description = "Returns task counts, completion percentage, and recent completion stats for the authenticated user.")
    public ResponseEntity<ApiResponse<DashboardOverviewResponse>> getOverview() {
        DashboardOverviewResponse response = analyticsService.getOverview();
        return ResponseEntity.ok(ApiResponse.success("Dashboard overview retrieved.", response));
    }

    @GetMapping("/daily")
    @Operation(summary = "Get daily completion stats", description = "Returns daily task completion counts for the given date range. Defaults to last 30 days.")
    public ResponseEntity<ApiResponse<List<DailyCompletionResponse>>> getDailyStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<DailyCompletionResponse> response = analyticsService.getDailyStats(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Daily stats retrieved.", response));
    }

    @GetMapping("/weekly")
    @Operation(summary = "Get weekly completion stats", description = "Returns weekly task completion counts for the given date range. Defaults to last 12 weeks.")
    public ResponseEntity<ApiResponse<List<WeeklyCompletionResponse>>> getWeeklyStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<WeeklyCompletionResponse> response = analyticsService.getWeeklyStats(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Weekly stats retrieved.", response));
    }

    @GetMapping("/monthly")
    @Operation(summary = "Get monthly completion stats", description = "Returns monthly task completion counts for the given date range. Defaults to last 12 months.")
    public ResponseEntity<ApiResponse<List<MonthlyCompletionResponse>>> getMonthlyStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<MonthlyCompletionResponse> response = analyticsService.getMonthlyStats(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Monthly stats retrieved.", response));
    }

    @GetMapping("/trends")
    @Operation(summary = "Get completion trends", description = "Returns daily stats, average completion time, completion rate, and productivity insights.")
    public ResponseEntity<ApiResponse<TrendsResponse>> getTrends(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        TrendsResponse response = analyticsService.getTrends(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Trends retrieved.", response));
    }

    @GetMapping("/distribution")
    @Operation(summary = "Get task distribution", description = "Returns task counts grouped by priority and category.")
    public ResponseEntity<ApiResponse<DistributionResponse>> getDistribution() {
        DistributionResponse response = analyticsService.getDistribution();
        return ResponseEntity.ok(ApiResponse.success("Distribution retrieved.", response));
    }

    @GetMapping("/insights")
    @Operation(summary = "Get productivity insights", description = "Returns rule-based insights generated from actual user data.")
    public ResponseEntity<ApiResponse<InsightsResponse>> getInsights() {
        InsightsResponse response = analyticsService.getInsights();
        return ResponseEntity.ok(ApiResponse.success("Insights retrieved.", response));
    }

    @PostMapping("/goals")
    @Operation(summary = "Create productivity goal", description = "Creates a new daily, weekly, or monthly task completion target.")
    public ResponseEntity<ApiResponse<GoalResponse>> createGoal(
            @Valid @RequestBody CreateGoalRequest request) {
        GoalResponse response = analyticsService.createGoal(request);
        return ResponseEntity.ok(ApiResponse.success("Goal created.", response));
    }

    @GetMapping("/goals")
    @Operation(summary = "List productivity goals", description = "Returns all active goals for the authenticated user.")
    public ResponseEntity<ApiResponse<List<GoalResponse>>> listGoals() {
        List<GoalResponse> response = analyticsService.listGoals();
        return ResponseEntity.ok(ApiResponse.success("Goals retrieved.", response));
    }

    @PutMapping("/goals/{goalId}")
    @Operation(summary = "Update productivity goal", description = "Updates an existing goal's target, period, or active status.")
    public ResponseEntity<ApiResponse<GoalResponse>> updateGoal(
            @PathVariable Long goalId,
            @Valid @RequestBody UpdateGoalRequest request) {
        GoalResponse response = analyticsService.updateGoal(goalId, request);
        return ResponseEntity.ok(ApiResponse.success("Goal updated.", response));
    }

    @DeleteMapping("/goals/{goalId}")
    @Operation(summary = "Delete productivity goal", description = "Deletes a productivity goal.")
    public ResponseEntity<ApiResponse<Void>> deleteGoal(@PathVariable Long goalId) {
        analyticsService.deleteGoal(goalId);
        return ResponseEntity.ok(ApiResponse.success("Goal deleted.", null));
    }
}
