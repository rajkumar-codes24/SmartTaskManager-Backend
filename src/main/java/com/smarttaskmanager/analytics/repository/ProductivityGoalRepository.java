package com.smarttaskmanager.analytics.repository;

import com.smarttaskmanager.analytics.entity.ProductivityGoal;
import com.smarttaskmanager.task.entity.TaskPriority;
import com.smarttaskmanager.task.entity.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for ProductivityGoal entities and analytics queries.
 */
@Repository
public interface ProductivityGoalRepository extends JpaRepository<ProductivityGoal, Long> {

    List<ProductivityGoal> findByUserIdAndIsActiveTrue(Long userId);

    Optional<ProductivityGoal> findByIdAndUserId(Long id, Long userId);

    /**
     * Count tasks completed in a date range for a user.
     */
    @Query("SELECT COUNT(t) FROM Task t WHERE t.user.id = :userId AND t.status = :status AND t.completedAt >= :start AND t.completedAt < :end")
    long countCompletedInRange(@Param("userId") Long userId,
                               @Param("status") TaskStatus status,
                               @Param("start") Instant start,
                               @Param("end") Instant end);

    /**
     * Count tasks by category for a user.
     */
    @Query("SELECT t.category.name AS categoryName, COUNT(t) AS count FROM Task t WHERE t.user.id = :userId AND t.category IS NOT NULL GROUP BY t.category.name")
    List<CategoryCountProjection> countTasksByCategoryForUser(@Param("userId") Long userId);

    /**
     * Find completed tasks in a date range for aggregation.
     */
    @Query("SELECT t.completedAt FROM Task t WHERE t.user.id = :userId AND t.status = :status AND t.completedAt >= :start AND t.completedAt < :end")
    List<Instant> findCompletedAtInRange(@Param("userId") Long userId,
                                         @Param("status") TaskStatus status,
                                         @Param("start") Instant start,
                                         @Param("end") Instant end);


    /**
     * Count tasks with approaching deadlines (within N days).
     */
    @Query("SELECT COUNT(t) FROM Task t WHERE t.user.id = :userId AND t.status NOT IN :excludedStatuses AND t.dueDate IS NOT NULL AND t.dueDate >= :now AND t.dueDate <= :threshold")
    long countTasksWithApproachingDeadline(@Param("userId") Long userId,
                                           @Param("excludedStatuses") List<TaskStatus> excludedStatuses,
                                           @Param("now") Instant now,
                                           @Param("threshold") Instant threshold);

    /**
     * Count overdue tasks by category.
     */
    @Query("SELECT t.category.name AS categoryName, COUNT(t) AS count FROM Task t WHERE t.user.id = :userId AND t.status NOT IN :excludedStatuses AND t.dueDate < :threshold AND t.category IS NOT NULL GROUP BY t.category.name")
    List<CategoryOverdueProjection> countOverdueByCategory(@Param("userId") Long userId,
                                                           @Param("excludedStatuses") List<TaskStatus> excludedStatuses,
                                                           @Param("threshold") Instant threshold);

    interface CategoryCountProjection {
        String getCategoryName();
        long getCount();
    }

    interface DailyCompletionProjection {
        LocalDate getDay();
        long getCount();
    }

    interface WeeklyCompletionProjection {
        String getWeekKey();
        long getCount();
    }

    interface MonthlyCompletionProjection {
        String getMonthKey();
        long getCount();
    }

    interface CategoryOverdueProjection {
        String getCategoryName();
        long getCount();
    }
}
