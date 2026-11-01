package com.smarttaskmanager.task.entity;

import com.smarttaskmanager.common.entity.BaseAuditableEntity;
import com.smarttaskmanager.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Recurrence rule entity for lazy occurrence generation of recurring tasks.
 */
@Entity
@Table(name = "task_recurrence_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskRecurrenceRule extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "recurrence_type", nullable = false, length = 30)
    private RecurrenceType recurrenceType;

    @Column(name = "recurrence_interval", nullable = false)
    @Builder.Default
    private Integer recurrenceInterval = 1;

    @Column(name = "week_days", length = 255)
    private String weekDays;

    @Column(name = "month_day")
    private Integer monthDay;

    @Column(name = "month_week", length = 50)
    private String monthWeek;

    @Column(name = "occurrence_count")
    private Integer occurrenceCount;

    @Column(name = "generated_occurrences", nullable = false)
    @Builder.Default
    private Integer generatedOccurrences = 0;

    @Column(name = "end_date")
    private Instant endDate;

    @Column(name = "is_paused", nullable = false)
    @Builder.Default
    private Boolean isPaused = false;

    @Column(name = "is_completed", nullable = false)
    @Builder.Default
    private Boolean isCompleted = false;

    @Column(name = "last_occurrence_at")
    private Instant lastOccurrenceAt;

    @Column(name = "next_occurrence_at")
    private Instant nextOccurrenceAt;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 0;
}
