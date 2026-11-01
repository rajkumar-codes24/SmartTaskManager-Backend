-- V8__create_subtasks_table.sql
-- Subtasks allow breaking a task into smaller, ordered subtasks

CREATE TABLE subtasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    parent_task_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    title VARCHAR(150) NOT NULL,
    description TEXT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'TODO',
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    due_date TIMESTAMP(6) NULL,
    completed_at TIMESTAMP(6) NULL,
    position INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_subtasks_parent FOREIGN KEY (parent_task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT fk_subtasks_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_subtasks_parent ON subtasks (parent_task_id);
CREATE INDEX idx_subtasks_user ON subtasks (user_id);
