package com.smarttaskmanager.task.repository;

import com.smarttaskmanager.task.entity.Subtask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Subtask entities.
 */
@Repository
public interface SubtaskRepository extends JpaRepository<Subtask, Long> {

    @Query("SELECT s FROM Subtask s WHERE s.parentTask.id = :taskId AND s.user.id = :userId ORDER BY s.position ASC")
    List<Subtask> findByParentTaskIdAndUserId(@Param("taskId") Long taskId, @Param("userId") Long userId);

    @Query("SELECT s FROM Subtask s WHERE s.id = :subtaskId AND s.user.id = :userId")
    Subtask findByIdAndUserId(@Param("subtaskId") Long subtaskId, @Param("userId") Long userId);

    @Query("SELECT COUNT(s) FROM Subtask s WHERE s.parentTask.id = :taskId AND s.user.id = :userId AND s.status = 'COMPLETED'")
    long countCompletedByParentTaskIdAndUserId(@Param("taskId") Long taskId, @Param("userId") Long userId);

    @Query("SELECT COUNT(s) FROM Subtask s WHERE s.parentTask.id = :taskId AND s.user.id = :userId")
    long countByParentTaskIdAndUserId(@Param("taskId") Long taskId, @Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM Subtask s WHERE s.parentTask.id = :taskId AND s.user.id = :userId")
    void deleteByParentTaskIdAndUserId(@Param("taskId") Long taskId, @Param("userId") Long userId);
}
