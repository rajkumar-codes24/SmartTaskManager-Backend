-- V14__add_version_and_completed_to_recurrence_rules.sql
-- Add optimistic locking version column and is_completed flag for recurrence rules

ALTER TABLE task_recurrence_rules ADD COLUMN version INT NOT NULL DEFAULT 0;
ALTER TABLE task_recurrence_rules ADD COLUMN is_completed BOOLEAN NOT NULL DEFAULT FALSE;
