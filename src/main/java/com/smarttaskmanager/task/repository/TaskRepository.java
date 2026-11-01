package com.smarttaskmanager.task.repository;

import com.smarttaskmanager.task.entity.Task;
import com.smarttaskmanager.task.entity.TaskPriority;
import com.smarttaskmanager.task.entity.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    Optional<Task> findByIdAndUserId(Long id, Long userId);

    Page<Task> findByUserId(Long userId, Pageable pageable);

    long countByUserId(Long userId);

    long countByUserIdAndStatus(Long userId, TaskStatus status);

    long countByUserIdAndStatusNotAndDueDateBefore(Long userId, TaskStatus status, Instant threshold);

    /**
     * Counts overdue tasks: tasks with a due date in the past that are not COMPLETED or CANCELLED.
     *
     * @param userId the user ID
     * @param statuses the statuses to exclude (e.g., COMPLETED, CANCELLED)
     * @param threshold the time threshold (usually now)
     * @return count of overdue tasks
     */
    long countByUserIdAndStatusNotInAndDueDateBefore(Long userId, java.util.List<TaskStatus> statuses, Instant threshold);

    @Query("SELECT t.status AS status, COUNT(t) AS count FROM Task t WHERE t.user.id = :userId GROUP BY t.status")
    List<StatusCountProjection> countTasksByStatusForUser(@Param("userId") Long userId);

    @Query("SELECT t.priority AS priority, COUNT(t) AS count FROM Task t WHERE t.user.id = :userId GROUP BY t.priority")
    List<PriorityCountProjection> countTasksByPriorityForUser(@Param("userId") Long userId);

    /**
     * Sets the category reference to NULL for all tasks belonging to a specific category.
     * Used before deleting a category to preserve referential integrity.
     *
     * @param categoryId the ID of the category to detach
     * @return the number of tasks updated
     */
    @Modifying
    @Query("UPDATE Task t SET t.category = NULL WHERE t.category.id = :categoryId")
    int clearCategoryIdById(@Param("categoryId") Long categoryId);

    interface StatusCountProjection {
        TaskStatus getStatus();
        long getCount();
    }

    interface PriorityCountProjection {
        TaskPriority getPriority();
        long getCount();
    }
}
