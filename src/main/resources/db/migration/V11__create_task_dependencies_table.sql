-- V11__create_task_dependencies_table.sql
-- Task dependencies define execution order between tasks

CREATE TABLE task_dependencies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    dependency_task_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_task_dependency (task_id, dependency_task_id),
    CONSTRAINT fk_deps_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT fk_deps_dependency FOREIGN KEY (dependency_task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT fk_deps_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_deps_no_self CHECK (task_id != dependency_task_id)
);

CREATE INDEX idx_deps_dependency ON task_dependencies (dependency_task_id);
