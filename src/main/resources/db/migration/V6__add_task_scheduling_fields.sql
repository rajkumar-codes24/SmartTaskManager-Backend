-- V6__add_task_scheduling_fields.sql
-- Extend tasks table with scheduling-related fields

ALTER TABLE tasks ADD COLUMN estimated_duration_minutes INT NULL;
ALTER TABLE tasks ADD COLUMN scheduled_start_at TIMESTAMP(6) NULL;
ALTER TABLE tasks ADD COLUMN scheduled_end_at TIMESTAMP(6) NULL;
ALTER TABLE tasks ADD COLUMN actual_start_at TIMESTAMP(6) NULL;
ALTER TABLE tasks ADD COLUMN actual_completed_at TIMESTAMP(6) NULL;
ALTER TABLE tasks ADD COLUMN deadline TIMESTAMP(6) NULL;
ALTER TABLE tasks ADD COLUMN timezone VARCHAR(63) NULL DEFAULT 'UTC';
ALTER TABLE tasks ADD COLUMN scheduling_status VARCHAR(20) NULL DEFAULT 'UNSCHEDULED';
