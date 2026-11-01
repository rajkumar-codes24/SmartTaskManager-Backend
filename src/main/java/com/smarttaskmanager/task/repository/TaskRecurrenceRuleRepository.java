package com.smarttaskmanager.task.repository;

import com.smarttaskmanager.task.entity.TaskRecurrenceRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for TaskRecurrenceRule entities.
 */
@Repository
public interface TaskRecurrenceRuleRepository extends JpaRepository<TaskRecurrenceRule, Long> {

    @Query("SELECT r FROM TaskRecurrenceRule r JOIN FETCH r.task WHERE r.task.id = :taskId AND r.user.id = :userId")
    Optional<TaskRecurrenceRule> findByTaskIdAndUserId(@Param("taskId") Long taskId, @Param("userId") Long userId);

    @Query("SELECT r FROM TaskRecurrenceRule r WHERE r.user.id = :userId AND r.isPaused = false AND r.nextOccurrenceAt <= :now")
    List<TaskRecurrenceRule> findDueUnpausedRules(@Param("userId") Long userId, @Param("now") java.time.Instant now);

    @Query("SELECT r FROM TaskRecurrenceRule r WHERE r.user.id = :userId")
    List<TaskRecurrenceRule> findByUserId(@Param("userId") Long userId);
}
