-- V7__create_user_scheduling_preferences_table.sql
-- User scheduling preferences for the smart scheduling engine

CREATE TABLE user_scheduling_preferences (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    work_day_start_time TIME NOT NULL DEFAULT '09:00:00',
    work_day_end_time TIME NOT NULL DEFAULT '17:00:00',
    work_days_of_week VARCHAR(255) NOT NULL DEFAULT 'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY',
    default_task_duration_minutes INT NOT NULL DEFAULT 60,
    break_duration_minutes INT NOT NULL DEFAULT 15,
    timezone VARCHAR(63) NOT NULL DEFAULT 'UTC',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_scheduling_prefs_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_scheduling_prefs_user UNIQUE (user_id)
);
