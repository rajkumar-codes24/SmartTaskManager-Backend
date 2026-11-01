-- V9__create_task_recurrence_rules_table.sql
-- Recurrence rules for recurring tasks (lazy occurrence generation)

CREATE TABLE task_recurrence_rules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    recurrence_type VARCHAR(30) NOT NULL,
    recurrence_interval INT NOT NULL DEFAULT 1,
    week_days VARCHAR(255) NULL,
    month_day INT NULL,
    month_week VARCHAR(50) NULL,
    occurrence_count INT NULL,
    end_date TIMESTAMP(6) NULL,
    is_paused BOOLEAN NOT NULL DEFAULT FALSE,
    last_occurrence_at TIMESTAMP(6) NULL,
    next_occurrence_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_recurrence_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT fk_recurrence_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_recurrence_task UNIQUE (task_id)
);
