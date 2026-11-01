package com.smarttaskmanager.analytics.service;

import com.smarttaskmanager.analytics.dto.*;
import com.smarttaskmanager.analytics.entity.ProductivityGoal;
import com.smarttaskmanager.analytics.repository.ProductivityGoalRepository;
import com.smarttaskmanager.common.exception.BusinessRuleException;
import com.smarttaskmanager.common.exception.ResourceNotFoundException;
import com.smarttaskmanager.task.entity.TaskPriority;
import com.smarttaskmanager.task.entity.TaskStatus;
import com.smarttaskmanager.task.repository.TaskRepository;
import com.smarttaskmanager.user.entity.User;
import com.smarttaskmanager.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for productivity analytics, insights, and goal management.
 * All analytics are scoped to the authenticated user.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final TaskRepository taskRepository;
    private final ProductivityGoalRepository goalRepository;
    private final UserRepository userRepository;

    /**
     * Gets the authenticated user.
     */
    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    // ==================== DASHBOARD OVERVIEW ====================

    /**
     * Returns dashboard overview analytics for the authenticated user.
     * <p>
     * Completion percentage = (completedTasks / totalTasks) * 100
     * Overdue = tasks with dueDate in the past and status not COMPLETED or CANCELLED
     * </p>
     */
    @Transactional(readOnly = true)
    public DashboardOverviewResponse getOverview() {
        User user = getCurrentUser();
        Long userId = user.getId();

        long totalTasks = taskRepository.countByUserId(userId);
        long completedTasks = taskRepository.countByUserIdAndStatus(userId, TaskStatus.COMPLETED);
        long inProgressTasks = taskRepository.countByUserIdAndStatus(userId, TaskStatus.IN_PROGRESS);
        long pendingTasks = totalTasks - completedTasks - inProgressTasks -
                taskRepository.countByUserIdAndStatus(userId, TaskStatus.CANCELLED);

        // Overdue: due date in the past, not COMPLETED or CANCELLED
        Instant now = Instant.now();
        long overdueTasks = taskRepository.countByUserIdAndStatusNotInAndDueDateBefore(
                userId, List.of(TaskStatus.COMPLETED, TaskStatus.CANCELLED), now);

        // Completion percentage
        double completionPercentage = totalTasks > 0
                ? (double) completedTasks / totalTasks * 100.0
                : 0.0;

        // Completed today
        ZonedDateTime todayStart = LocalDate.now(ZoneId.of("UTC")).atStartOfDay(ZoneId.of("UTC"));
        ZonedDateTime tomorrowStart = todayStart.plusDays(1);
        long completedToday = goalRepository.countCompletedInRange(
                userId, TaskStatus.COMPLETED, todayStart.toInstant(), tomorrowStart.toInstant());

        // Completed this week
        ZonedDateTime weekStart = LocalDate.now(ZoneId.of("UTC"))
                .with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                .atStartOfDay(ZoneId.of("UTC"));
        ZonedDateTime nextWeekStart = weekStart.plusWeeks(1);
        long completedThisWeek = goalRepository.countCompletedInRange(
                userId, TaskStatus.COMPLETED, weekStart.toInstant(), nextWeekStart.toInstant());

        // Completed this month
        ZonedDateTime monthStart = LocalDate.now(ZoneId.of("UTC"))
                .with(TemporalAdjusters.firstDayOfMonth())
                .atStartOfDay(ZoneId.of("UTC"));
        ZonedDateTime nextMonthStart = monthStart.plusMonths(1);
        long completedThisMonth = goalRepository.countCompletedInRange(
                userId, TaskStatus.COMPLETED, monthStart.toInstant(), nextMonthStart.toInstant());

        return new DashboardOverviewResponse(
                totalTasks,
                completedTasks,
                pendingTasks,
                inProgressTasks,
                overdueTasks,
                Math.round(completionPercentage * 100.0) / 100.0,
                completedToday,
                completedThisWeek,
                completedThisMonth
        );
    }

    // ==================== HISTORICAL ANALYTICS ====================

    /**
     * Returns daily completion statistics for the given date range.
     * If no range is provided, defaults to the last 30 days.
     */
    @Transactional(readOnly = true)
    public List<DailyCompletionResponse> getDailyStats(LocalDate startDate, LocalDate endDate) {
        User user = getCurrentUser();
        Long userId = user.getId();

        if (startDate == null) {
            startDate = LocalDate.now(ZoneId.of("UTC")).minusDays(30);
        }
        if (endDate == null) {
            endDate = LocalDate.now(ZoneId.of("UTC"));
        }

        Instant start = startDate.atStartOfDay(ZoneId.of("UTC")).toInstant();
        Instant end = endDate.plusDays(1).atStartOfDay(ZoneId.of("UTC")).toInstant();

        List<Instant> completedAtList = goalRepository.findCompletedAtInRange(userId, TaskStatus.COMPLETED, start, end);

        // Group by day
        Map<LocalDate, Long> byDay = completedAtList.stream()
                .collect(Collectors.groupingBy(
                        inst -> inst.atZone(ZoneId.of("UTC")).toLocalDate(),
                        Collectors.counting()
                ));

        return byDay.entrySet().stream()
                .map(e -> new DailyCompletionResponse(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(DailyCompletionResponse::date))
                .collect(Collectors.toList());
    }

    /**
     * Returns weekly completion statistics for the given date range.
     * If no range is provided, defaults to the last 12 weeks.
     */
    @Transactional(readOnly = true)
    public List<WeeklyCompletionResponse> getWeeklyStats(LocalDate startDate, LocalDate endDate) {
        User user = getCurrentUser();
        Long userId = user.getId();

        if (startDate == null) {
            startDate = LocalDate.now(ZoneId.of("UTC")).minusWeeks(12);
        }
        if (endDate == null) {
            endDate = LocalDate.now(ZoneId.of("UTC"));
        }

        Instant start = startDate.atStartOfDay(ZoneId.of("UTC")).toInstant();
        Instant end = endDate.plusDays(1).atStartOfDay(ZoneId.of("UTC")).toInstant();

        List<Instant> completedAtList = goalRepository.findCompletedAtInRange(userId, TaskStatus.COMPLETED, start, end);

        // Group by ISO week
        Map<String, Long> byWeek = completedAtList.stream()
                .collect(Collectors.groupingBy(
                        inst -> {
                            LocalDate date = inst.atZone(ZoneId.of("UTC")).toLocalDate();
                            int week = date.get(WeekFields.ISO.weekOfWeekBasedYear());
                            int year = date.get(WeekFields.ISO.weekBasedYear());
                            return String.format("%d-W%02d", year, week);
                        },
                        Collectors.counting()
                ));

        return byWeek.entrySet().stream()
                .map(e -> new WeeklyCompletionResponse(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(WeeklyCompletionResponse::week))
                .collect(Collectors.toList());
    }

    /**
     * Returns monthly completion statistics for the given date range.
     * If no range is provided, defaults to the last 12 months.
     */
    @Transactional(readOnly = true)
    public List<MonthlyCompletionResponse> getMonthlyStats(LocalDate startDate, LocalDate endDate) {
        User user = getCurrentUser();
        Long userId = user.getId();

        if (startDate == null) {
            startDate = LocalDate.now(ZoneId.of("UTC")).minusMonths(12);
        }
        if (endDate == null) {
            endDate = LocalDate.now(ZoneId.of("UTC"));
        }

        Instant start = startDate.atStartOfDay(ZoneId.of("UTC")).toInstant();
        Instant end = endDate.plusDays(1).atStartOfDay(ZoneId.of("UTC")).toInstant();

        List<Instant> completedAtList = goalRepository.findCompletedAtInRange(userId, TaskStatus.COMPLETED, start, end);

        // Group by month
        Map<String, Long> byMonth = completedAtList.stream()
                .collect(Collectors.groupingBy(
                        inst -> {
                            LocalDate date = inst.atZone(ZoneId.of("UTC")).toLocalDate();
                            return String.format("%d-%02d", date.getYear(), date.getMonthValue());
                        },
                        Collectors.counting()
                ));

        return byMonth.entrySet().stream()
                .map(e -> new MonthlyCompletionResponse(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(MonthlyCompletionResponse::month))
                .collect(Collectors.toList());
    }

    // ==================== TRENDS ====================

    /**
     * Returns completion trends for the authenticated user.
     * <p>
     * Average completion time = mean of (completedAt - createdAt) for completed tasks, in minutes.
     * Completion rate = completedTasks / totalTasks (0-1 scale).
     * </p>
     */
    @Transactional(readOnly = true)
    public TrendsResponse getTrends(LocalDate startDate, LocalDate endDate) {
        User user = getCurrentUser();
        Long userId = user.getId();

        // Daily stats for the period
        List<DailyCompletionResponse> dailyStats = getDailyStats(startDate, endDate);

        // Average completion time - fetch all completed tasks and calculate in Java
        double avgMinutes = calculateAverageCompletionTimeMinutes(userId);

        // Completion rate
        long totalTasks = taskRepository.countByUserId(userId);
        long completedTasks = taskRepository.countByUserIdAndStatus(userId, TaskStatus.COMPLETED);
        double completionRate = totalTasks > 0
                ? (double) completedTasks / totalTasks
                : 0.0;

        // Generate insights
        List<String> insights = generateInsights(userId);

        return new TrendsResponse(
                dailyStats,
                Math.round(avgMinutes * 100.0) / 100.0,
                Math.round(completionRate * 10000.0) / 10000.0,
                insights
        );
    }

    // ==================== DISTRIBUTION ====================

    /**
     * Returns task distribution by priority and category.
     */
    @Transactional(readOnly = true)
    public DistributionResponse getDistribution() {
        User user = getCurrentUser();
        Long userId = user.getId();

        // Priority distribution using projection queries
        List<TaskRepository.PriorityCountProjection> priorityProjections =
                taskRepository.countTasksByPriorityForUser(userId);
        Map<TaskPriority, Long> priorityMap = new EnumMap<>(TaskPriority.class);
        for (TaskPriority p : TaskPriority.values()) {
            priorityMap.put(p, 0L);
        }
        for (TaskRepository.PriorityCountProjection p : priorityProjections) {
            priorityMap.put(p.getPriority(), p.getCount());
        }

        // Category distribution
        List<ProductivityGoalRepository.CategoryCountProjection> categoryProjections =
                goalRepository.countTasksByCategoryForUser(userId);
        Map<String, Long> categoryMap = new LinkedHashMap<>();
        for (ProductivityGoalRepository.CategoryCountProjection p : categoryProjections) {
            categoryMap.put(p.getCategoryName(), p.getCount());
        }

        return new DistributionResponse(priorityMap, categoryMap);
    }

    // ==================== INSIGHTS ====================

    /**
     * Generates rule-based productivity insights from actual user data.
     * Insights are grounded in real data, not fabricated.
     */
    @Transactional(readOnly = true)
    public InsightsResponse getInsights() {
        User user = getCurrentUser();
        Long userId = user.getId();
        List<String> messages = generateInsights(userId);

        List<InsightItem> items = messages.stream()
                .map(msg -> new InsightItem("PRODUCTIVITY", msg, insightSeverity(msg)))
                .collect(Collectors.toList());

        return new InsightsResponse(items);
    }

    private List<String> generateInsights(Long userId) {
        List<String> insights = new ArrayList<>();

        long totalTasks = taskRepository.countByUserId(userId);
        if (totalTasks == 0) {
            insights.add("You have no tasks yet. Create your first task to start tracking productivity.");
            return insights;
        }

        long completedTasks = taskRepository.countByUserIdAndStatus(userId, TaskStatus.COMPLETED);
        long overdueTasks = taskRepository.countByUserIdAndStatusNotInAndDueDateBefore(
                userId, List.of(TaskStatus.COMPLETED, TaskStatus.CANCELLED), Instant.now());

        // Overdue tasks insight
        if (overdueTasks > 0) {
            insights.add("You have " + overdueTasks + " overdue task" +
                    (overdueTasks > 1 ? "s" : "") + " that need attention.");
        }

        // High-priority accumulation
        long highPriorityPending = taskRepository.countByUserIdAndStatus(userId, TaskStatus.TODO) +
                taskRepository.countByUserIdAndStatus(userId, TaskStatus.IN_PROGRESS);
        long urgentTasks = 0;
        for (TaskRepository.PriorityCountProjection p : taskRepository.countTasksByPriorityForUser(userId)) {
            if (p.getPriority() == TaskPriority.URGENT || p.getPriority() == TaskPriority.HIGH) {
                urgentTasks += p.getCount();
            }
        }
        if (urgentTasks > 5) {
            insights.add("You have " + urgentTasks + " high-priority or urgent tasks. Consider focusing on these first.");
        }

        // Approaching deadlines (within 3 days)
        Instant now = Instant.now();
        Instant threeDaysLater = now.plus(3, ChronoUnit.DAYS);
        long approachingDeadlines = goalRepository.countTasksWithApproachingDeadline(
                userId, List.of(TaskStatus.COMPLETED, TaskStatus.CANCELLED), now, threeDaysLater);
        if (approachingDeadlines > 0) {
            insights.add("You have " + approachingDeadlines + " task" +
                    (approachingDeadlines > 1 ? "s" : "") + " with deadlines in the next 3 days.");
        }

        // Weekly completion comparison
        ZonedDateTime thisWeekStart = LocalDate.now(ZoneId.of("UTC"))
                .with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                .atStartOfDay(ZoneId.of("UTC"));
        ZonedDateTime thisWeekEnd = thisWeekStart.plusWeeks(1);
        long completedThisWeek = goalRepository.countCompletedInRange(
                userId, TaskStatus.COMPLETED, thisWeekStart.toInstant(), thisWeekEnd.toInstant());

        ZonedDateTime lastWeekStart = thisWeekStart.minusWeeks(1);
        ZonedDateTime lastWeekEnd = thisWeekStart;
        long completedLastWeek = goalRepository.countCompletedInRange(
                userId, TaskStatus.COMPLETED, lastWeekStart.toInstant(), lastWeekEnd.toInstant());

        if (completedLastWeek > 0 && completedThisWeek > 0) {
            if (completedThisWeek > completedLastWeek) {
                insights.add("You completed " + completedThisWeek + " tasks this week, " +
                        "up from " + completedLastWeek + " last week. Keep it up!");
            } else if (completedThisWeek < completedLastWeek) {
                insights.add("You completed " + completedThisWeek + " tasks this week, " +
                        "down from " + completedLastWeek + " last week.");
            }
        }

        // Category overdue ratio
        List<ProductivityGoalRepository.CategoryOverdueProjection> overdueByCategory =
                goalRepository.countOverdueByCategory(
                        userId, List.of(TaskStatus.COMPLETED, TaskStatus.CANCELLED), now);
        for (ProductivityGoalRepository.CategoryOverdueProjection p : overdueByCategory) {
            if (p.getCount() >= 3) {
                insights.add("The '" + p.getCategoryName() + "' category has " +
                        p.getCount() + " overdue tasks. Consider reprioritizing this category.");
            }
        }

        // Completion rate insight
        double completionRate = (double) completedTasks / totalTasks;
        if (totalTasks >= 5 && completionRate < 0.3) {
            insights.add("Your completion rate is " +
                    String.format("%.0f", completionRate * 100) + "%. " +
                    "Consider breaking tasks into smaller subtasks to improve completion.");
        } else if (totalTasks >= 5 && completionRate >= 0.8) {
            insights.add("Your completion rate is " +
                    String.format("%.0f", completionRate * 100) + "%. Excellent productivity!");
        }

        // Average completion time insight
        double avgMinutes = calculateAverageCompletionTimeMinutes(userId);
        if (avgMinutes > 0 && completedTasks >= 3) {
            double avgHours = avgMinutes / 60.0;
            if (avgHours > 24) {
                insights.add("Your average task completion time is " +
                        String.format("%.1f", avgHours) + " hours. " +
                        "Consider setting more realistic deadlines or breaking tasks down.");
            }
        }

        return insights;
    }

    private int insightSeverity(String message) {
        if (message.contains("overdue") || message.contains("high-priority")) {
            return 3; // High severity
        } else if (message.contains("deadline") || message.contains("down from")) {
            return 2; // Medium severity
        } else {
            return 1; // Low severity
        }
    }

    // ==================== GOALS ====================

    /**
     * Creates a new productivity goal for the authenticated user.
     */
    @Transactional
    public GoalResponse createGoal(CreateGoalRequest request) {
        User user = getCurrentUser();

        // Validate goal type
        ProductivityGoal.GoalType goalType;
        try {
            goalType = ProductivityGoal.GoalType.valueOf(request.goalType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Invalid goal type: " + request.goalType() +
                    ". Valid types: DAILY, WEEKLY, MONTHLY");
        }

        // Validate period
        if (!request.periodEndDate().isAfter(request.periodStartDate())) {
            throw new BusinessRuleException("Period end date must be after start date");
        }

        ProductivityGoal goal = ProductivityGoal.builder()
                .user(user)
                .goalType(goalType)
                .targetCount(request.targetCount())
                .periodStartDate(request.periodStartDate())
                .periodEndDate(request.periodEndDate())
                .currentCount(0)
                .isActive(true)
                .build();

        goal = goalRepository.save(goal);
        log.info("Productivity goal created for user [{}]: {} target {} tasks",
                user.getEmail(), goalType, request.targetCount());

        return GoalResponse.fromEntity(goal);
    }

    /**
     * Lists active goals for the authenticated user.
     */
    @Transactional(readOnly = true)
    public List<GoalResponse> listGoals() {
        User user = getCurrentUser();
        List<ProductivityGoal> goals = goalRepository.findByUserIdAndIsActiveTrue(user.getId());

        // Update current counts based on actual completed tasks in the period
        goals.forEach(this::updateCurrentCount);

        return goals.stream()
                .map(GoalResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Gets a single goal by ID.
     */
    @Transactional(readOnly = true)
    public GoalResponse getGoal(Long goalId) {
        User user = getCurrentUser();
        ProductivityGoal goal = goalRepository.findByIdAndUserId(goalId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Goal", "id", goalId));

        updateCurrentCount(goal);
        return GoalResponse.fromEntity(goal);
    }

    /**
     * Updates a productivity goal.
     */
    @Transactional
    public GoalResponse updateGoal(Long goalId, UpdateGoalRequest request) {
        User user = getCurrentUser();
        ProductivityGoal goal = goalRepository.findByIdAndUserId(goalId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Goal", "id", goalId));

        if (request.goalType() != null) {
            try {
                goal.setGoalType(ProductivityGoal.GoalType.valueOf(request.goalType().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new BusinessRuleException("Invalid goal type: " + request.goalType());
            }
        }
        if (request.targetCount() != null) {
            goal.setTargetCount(request.targetCount());
        }
        if (request.periodStartDate() != null) {
            goal.setPeriodStartDate(request.periodStartDate());
        }
        if (request.periodEndDate() != null) {
            goal.setPeriodEndDate(request.periodEndDate());
        }
        if (request.isActive() != null) {
            goal.setIsActive(request.isActive());
        }

        goal = goalRepository.save(goal);
        return GoalResponse.fromEntity(goal);
    }

    /**
     * Deletes a productivity goal.
     */
    @Transactional
    public void deleteGoal(Long goalId) {
        User user = getCurrentUser();
        ProductivityGoal goal = goalRepository.findByIdAndUserId(goalId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Goal", "id", goalId));

        goalRepository.delete(goal);
        log.info("Productivity goal deleted for user [{}]: ID {}", user.getEmail(), goalId);
    }

    /**
     * Calculates average completion time in minutes for completed tasks.
     */
    private double calculateAverageCompletionTimeMinutes(Long userId) {
        List<com.smarttaskmanager.task.entity.Task> completedTasks = taskRepository.findAll(
                org.springframework.data.jpa.domain.Specification.where(
                        org.springframework.data.jpa.domain.Specification.<com.smarttaskmanager.task.entity.Task>where(
                                (root, query, cb) -> cb.equal(root.get("user").get("id"), userId)
                        ).and((root, query, cb) -> cb.equal(root.get("status"), TaskStatus.COMPLETED))
                                .and((root, query, cb) -> cb.isNotNull(root.get("completedAt")))
                ));

        if (completedTasks.isEmpty()) {
            return 0.0;
        }

        double totalSeconds = completedTasks.stream()
                .mapToDouble(t -> java.time.Duration.between(t.getCreatedAt(), t.getCompletedAt()).getSeconds())
                .sum();
        return totalSeconds / completedTasks.size() / 60.0;
    }

    /**
     * Updates the current count for a goal based on actual completed tasks in the period.
     */
    private void updateCurrentCount(ProductivityGoal goal) {
        if (!goal.getIsActive()) {
            return;
        }

        Instant start = goal.getPeriodStartDate().atStartOfDay(ZoneId.of("UTC")).toInstant();
        Instant end = goal.getPeriodEndDate().plusDays(1).atStartOfDay(ZoneId.of("UTC")).toInstant();

        long completed = goalRepository.countCompletedInRange(
                goal.getUser().getId(), TaskStatus.COMPLETED, start, end);

        goal.setCurrentCount((int) Math.min(completed, Integer.MAX_VALUE));
    }
}
