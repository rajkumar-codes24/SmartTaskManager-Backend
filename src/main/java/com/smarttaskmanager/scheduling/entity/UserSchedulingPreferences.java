package com.smarttaskmanager.scheduling.entity;

import com.smarttaskmanager.common.entity.BaseAuditableEntity;
import com.smarttaskmanager.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;
import java.util.Set;

/**
 * User scheduling preferences for the smart scheduling engine.
 * Stores working hours, default durations, and timezone preferences.
 */
@Entity
@Table(name = "user_scheduling_preferences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSchedulingPreferences extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "work_day_start_time", nullable = false)
    @Builder.Default
    private LocalTime workDayStartTime = LocalTime.of(9, 0);

    @Column(name = "work_day_end_time", nullable = false)
    @Builder.Default
    private LocalTime workDayEndTime = LocalTime.of(17, 0);

    @Column(name = "work_days_of_week", nullable = false)
    @Builder.Default
    private String workDaysOfWeek = "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY";

    @Column(name = "default_task_duration_minutes", nullable = false)
    @Builder.Default
    private Integer defaultTaskDurationMinutes = 60;

    @Column(name = "break_duration_minutes", nullable = false)
    @Builder.Default
    private Integer breakDurationMinutes = 15;

    @Column(name = "timezone", nullable = false, length = 63)
    @Builder.Default
    private String timezone = "UTC";

    /**
     * Returns the set of work day names as an unmodifiable set.
     */
    public Set<String> getWorkDaysSet() {
        if (workDaysOfWeek == null || workDaysOfWeek.trim().isEmpty()) {
            return Set.of();
        }
        return Set.of(workDaysOfWeek.split(","));
    }

    /**
     * Returns the available working minutes in a single work day.
     */
    public long getWorkingMinutesPerDay() {
        long start = workDayStartTime.getHour() * 60L + workDayStartTime.getMinute();
        long end = workDayEndTime.getHour() * 60L + workDayEndTime.getMinute();
        return Math.max(0, end - start);
    }
}
