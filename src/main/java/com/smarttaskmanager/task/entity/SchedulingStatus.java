package com.smarttaskmanager.task.entity;

/**
 * Scheduling status of a task in the smart scheduling engine.
 */
public enum SchedulingStatus {
    /** Task has not been scheduled yet. */
    UNSCHEDULED,
    /** Task is scheduled within a plan. */
    SCHEDULED,
    /** Task is in progress. */
    IN_PROGRESS,
    /** Task scheduling was skipped or cancelled. */
    SKIPPED
}
