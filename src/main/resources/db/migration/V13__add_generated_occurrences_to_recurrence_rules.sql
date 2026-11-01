-- V13__add_generated_occurrences_to_recurrence_rules.sql
-- Add generated_occurrences column to track actual occurrence count for recurrence rules

ALTER TABLE task_recurrence_rules ADD COLUMN generated_occurrences INT NOT NULL DEFAULT 0;
