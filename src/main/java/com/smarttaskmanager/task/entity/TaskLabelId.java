package com.smarttaskmanager.task.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite key for TaskLabel join table entity.
 */
public class TaskLabelId implements Serializable {

    private Long task;
    private Long label;

    public TaskLabelId() {}

    public TaskLabelId(Long task, Long label) {
        this.task = task;
        this.label = label;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TaskLabelId that = (TaskLabelId) o;
        return Objects.equals(task, that.task) && Objects.equals(label, that.label);
    }

    @Override
    public int hashCode() {
        return Objects.hash(task, label);
    }
}
