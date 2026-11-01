-- V5__create_reminders_table.sql
-- Table definition for task reminders

CREATE TABLE reminders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    scheduled_at TIMESTAMP(6) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    sent_at TIMESTAMP(6) NULL,
    retry_count INT NOT NULL DEFAULT 0,
    failure_reason VARCHAR(255) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_reminders_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT fk_reminders_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_reminders_status_scheduled ON reminders (status, scheduled_at);
CREATE INDEX idx_reminders_user ON reminders (user_id);
