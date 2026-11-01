package com.smarttaskmanager.task.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Join table entity for task-label relationships.
 * Exists solely so Hibernate can create the task_labels table when Flyway is disabled (test profile).
 */
@Entity
@Table(name = "task_labels")
@IdClass(TaskLabelId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskLabel {

    @Id
    @ManyToOne
    @JoinColumn(name = "task_id", nullable = false)
    private com.smarttaskmanager.task.entity.Task task;

    @Id
    @ManyToOne
    @JoinColumn(name = "label_id", nullable = false)
    private Label label;
}
