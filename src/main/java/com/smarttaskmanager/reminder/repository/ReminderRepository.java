package com.smarttaskmanager.reminder.repository;

import com.smarttaskmanager.reminder.entity.Reminder;
import com.smarttaskmanager.reminder.entity.ReminderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ReminderRepository extends JpaRepository<Reminder, Long> {

    @Query("SELECT r FROM Reminder r JOIN FETCH r.task t JOIN FETCH r.user u WHERE r.status = :status AND r.scheduledAt <= :threshold ORDER BY r.scheduledAt ASC")
    List<Reminder> findDueRemindersWithDetails(
            @Param("status") ReminderStatus status,
            @Param("threshold") Instant threshold
    );

    @Query("SELECT r FROM Reminder r JOIN FETCH r.task t WHERE r.user.id = :userId")
    List<Reminder> findByUserIdWithTask(@Param("userId") Long userId);

    List<Reminder> findByTaskId(Long taskId);

    List<Reminder> findByUserId(Long userId);
}
