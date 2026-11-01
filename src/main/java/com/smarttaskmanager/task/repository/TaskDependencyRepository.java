package com.smarttaskmanager.task.repository;

import com.smarttaskmanager.task.entity.TaskDependency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for TaskDependency entities.
 */
@Repository
public interface TaskDependencyRepository extends JpaRepository<TaskDependency, Long> {

    @Query("SELECT td FROM TaskDependency td JOIN FETCH td.task t JOIN FETCH td.dependencyTask dt WHERE td.task.id = :taskId AND td.user.id = :userId")
    List<TaskDependency> findByTaskIdAndUserId(@Param("taskId") Long taskId, @Param("userId") Long userId);

    @Query("SELECT td FROM TaskDependency td JOIN FETCH td.task t JOIN FETCH td.dependencyTask dt WHERE td.dependencyTask.id = :taskId AND td.user.id = :userId")
    List<TaskDependency> findByDependencyTaskIdAndUserId(@Param("taskId") Long taskId, @Param("userId") Long userId);

    @Query("SELECT CASE WHEN COUNT(td) > 0 THEN TRUE ELSE FALSE END FROM TaskDependency td WHERE td.task.id = :taskId AND td.dependencyTask.id = :dependencyTaskId AND td.user.id = :userId")
    boolean existsByTaskIdAndDependencyTaskIdAndUserId(@Param("taskId") Long taskId, @Param("dependencyTaskId") Long dependencyTaskId, @Param("userId") Long userId);

    @Query("SELECT td FROM TaskDependency td WHERE td.user.id = :userId")
    List<TaskDependency> findByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM TaskDependency td WHERE td.task.id = :taskId AND td.user.id = :userId")
    void deleteByTaskIdAndUserId(@Param("taskId") Long taskId, @Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM TaskDependency td WHERE td.dependencyTask.id = :taskId AND td.user.id = :userId")
    void deleteByDependencyTaskIdAndUserId(@Param("taskId") Long taskId, @Param("userId") Long userId);
}
