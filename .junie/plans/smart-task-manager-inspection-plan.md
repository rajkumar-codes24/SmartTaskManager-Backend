---
sessionId: session-261029-202720-14rr
---

# SmartTaskManager V2 — Advanced Features Implementation Plan

## Current Project Status

All 7 original milestones are complete with **384 passing tests** (357 original + 27 new for Step 11 Smart Scheduling Engine).

| Component | Version |
|---|---|
| Java | 21 |
| Spring Boot | 4.1.2 |
| Spring Security | 7.x (managed by Spring Boot 4.1.2) |
| JJWT | 0.12.6 |
| Springdoc OpenAPI | 3.0.0 |
| MySQL | 8.x |
| Flyway | managed by Spring Boot |
| H2 (test) | managed by Spring Boot |
| Server Port | 9999 |

**Architecture:** Layered Modular Monolith — packages by domain (auth, user, category, task, reminder, email, security, common).

**Existing patterns:**
- Controllers: `@RestController`, `@RequestMapping("/api/v1/...")`, `@SecurityRequirement`, `@Tag`, `@Operation`
- Services: `@Service`, `@RequiredArgsConstructor`, `@Slf4j`, `@Transactional`, ownership via `SecurityContextHolder`
- Repositories: `JpaRepository` + `JpaSpecificationExecutor`, ownership-scoped queries (`findByIdAndUserId`)
- DTOs: Records with Lombok `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`
- Entities: Extend `BaseAuditableEntity` (createdAt/updatedAt), use Lombok builders
- Tests: `@SpringBootTest`, `@AutoConfigureMockMvc`, `@ActiveProfiles("test")`, `@Transactional`, MockMvc
- Responses: `ApiResponse<T>` wrapper, `ErrorResponse` for errors, `GlobalExceptionHandler`

---

## 4 New Modules Overview

### Module 1: Smart Scheduling Engine
Intelligent task scheduling based on priorities, deadlines, durations, working hours, and dependencies.

### Module 2: Advanced Task Management
Subtasks, recurring tasks, labels/tags, and task dependencies with cycle detection.

### Module 3: Productivity Intelligence
Dashboard analytics, trends, insights, and productivity goals.

### Module 4: AI-Powered Task Capture
Natural language task parsing with OpenAI provider abstraction and rule-based fallback.

---

## Technical Design

### Database Schema Changes (Flyway V6–V12)

**V6 — Task scheduling fields (extend tasks table):**
```sql
ALTER TABLE tasks ADD COLUMN estimated_duration_minutes INT NULL;
ALTER TABLE tasks ADD COLUMN scheduled_start_at TIMESTAMP(6) NULL;
ALTER TABLE tasks ADD COLUMN scheduled_end_at TIMESTAMP(6) NULL;
ALTER TABLE tasks ADD COLUMN actual_start_at TIMESTAMP(6) NULL;
ALTER TABLE tasks ADD COLUMN actual_completed_at TIMESTAMP(6) NULL;
ALTER TABLE tasks ADD COLUMN deadline TIMESTAMP(6) NULL;
ALTER TABLE tasks ADD COLUMN timezone VARCHAR(63) NULL DEFAULT 'UTC';
ALTER TABLE tasks ADD COLUMN scheduling_status VARCHAR(20) NULL DEFAULT 'UNSCHEDULED';
```

**V7 — User scheduling preferences:**
```sql
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
```

**V8 — Subtasks:**
```sql
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
```

**V9 — Recurrence rules:**
```sql
CREATE TABLE task_recurrence_rules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    recurrence_type VARCHAR(30) NOT NULL,
    interval INT NOT NULL DEFAULT 1,
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
```

**V10 — Labels:**
```sql
CREATE TABLE labels (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    color VARCHAR(7) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_labels_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_user_label_name UNIQUE (user_id, name)
);
CREATE INDEX idx_labels_user ON labels (user_id);

CREATE TABLE task_labels (
    task_id BIGINT NOT NULL,
    label_id BIGINT NOT NULL,
    PRIMARY KEY (task_id, label_id),
    CONSTRAINT fk_task_labels_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT fk_task_labels_label FOREIGN KEY (label_id) REFERENCES labels (id) ON DELETE CASCADE
);
```

**V11 — Task dependencies:**
```sql
CREATE TABLE task_dependencies (
    task_id BIGINT NOT NULL,
    dependency_task_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (task_id, dependency_task_id),
    CONSTRAINT fk_deps_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT fk_deps_dependency FOREIGN KEY (dependency_task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT fk_deps_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_deps_no_self CHECK (task_id != dependency_task_id)
);
CREATE INDEX idx_deps_dependency ON task_dependencies (dependency_task_id);
```

**V12 — Productivity goals:**
```sql
CREATE TABLE productivity_goals (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    goal_type VARCHAR(20) NOT NULL,
    target_count INT NOT NULL,
    period_start_date DATE NOT NULL,
    period_end_date DATE NOT NULL,
    current_count INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_goals_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_goals_user_active ON productivity_goals (user_id, is_active);
```

### Key Architectural Decisions

1. **Scheduling Algorithm**: Full constraint-based with topological sorting for dependency order. Tasks are sorted by priority weight × urgency factor, then scheduled greedily within working hours respecting dependencies.

2. **Recurrence**: Lazy occurrence generation — store rules only, generate next occurrence on-demand when the current one is completed or when the scheduler runs. No pre-created task records.

3. **AI Provider**: Provider abstraction (`TaskCaptureProvider` interface) with `OpenAiTaskCaptureProvider` and `RuleBasedTaskCaptureProvider` implementations. Configurable via `AI_PROVIDER` env var.

4. **Dependency Cycle Detection**: DFS-based cycle detection before adding dependencies. Topological sort for execution order.

5. **Analytics**: Efficient DB-level aggregation queries using `@Query` with GROUP BY, not loading all entities into memory.

---

## New API Endpoints

### Scheduling (`/api/v1/scheduling`)
| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/preferences` | Get user scheduling preferences |
| PUT | `/preferences` | Update scheduling preferences |
| POST | `/plan` | Generate a proposed schedule |
| GET | `/plan` | Get the current proposed schedule |
| POST | `/plan/apply` | Apply the proposed schedule |
| GET | `/conflicts` | Get scheduling conflicts |
| GET | `/deadline-risks` | Get tasks at risk of missing deadlines |

### Subtasks (`/api/v1/tasks/{taskId}/subtasks`)
| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/tasks/{taskId}/subtasks` | Create a subtask |
| GET | `/tasks/{taskId}/subtasks` | List subtasks |
| PUT | `/tasks/{taskId}/subtasks/{subtaskId}` | Update a subtask |
| DELETE | `/tasks/{taskId}/subtasks/{subtaskId}` | Delete a subtask |

### Recurrence (`/api/v1/tasks/{taskId}/recurrence`)
| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/tasks/{taskId}/recurrence` | Create recurrence rule |
| GET | `/tasks/{taskId}/recurrence` | Get recurrence rule |
| PUT | `/tasks/{taskId}/recurrence` | Update recurrence rule |
| DELETE | `/tasks/{taskId}/recurrence` | Delete recurrence rule |
| POST | `/tasks/{taskId}/recurrence/pause` | Pause recurrence |
| POST | `/tasks/{taskId}/recurrence/resume` | Resume recurrence |

### Labels (`/api/v1/labels`)
| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/labels` | Create a label |
| GET | `/labels` | List user labels |
| PUT | `/labels/{labelId}` | Update a label |
| DELETE | `/labels/{labelId}` | Delete a label |
| POST | `/tasks/{taskId}/labels/{labelId}` | Assign label to task |
| DELETE | `/tasks/{taskId}/labels/{labelId}` | Remove label from task |

### Dependencies (`/api/v1/tasks/{taskId}/dependencies`)
| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/tasks/{taskId}/dependencies/{dependencyTaskId}` | Add dependency |
| DELETE | `/tasks/{taskId}/dependencies/{dependencyTaskId}` | Remove dependency |
| GET | `/tasks/{taskId}/dependencies` | List dependencies |
| GET | `/tasks/{taskId}/dependents` | List dependents |
| GET | `/tasks/{taskId}/dependency-status` | Get dependency completion status |

### Analytics (`/api/v1/analytics`)
| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/overview` | Dashboard overview |
| GET | `/daily` | Daily completion stats |
| GET | `/weekly` | Weekly completion stats |
| GET | `/monthly` | Monthly completion stats |
| GET | `/trends` | Completion trends |
| GET | `/distribution` | Priority/category distribution |
| GET | `/insights` | Rule-based productivity insights |
| POST | `/goals` | Create productivity goal |
| GET | `/goals` | List user goals |
| PUT | `/goals/{goalId}` | Update goal |
| DELETE | `/goals/{goalId}` | Delete goal |

### Task Capture (`/api/v1/task-capture`)
| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/parse` | Parse natural language into structured task |
| POST | `/confirm` | Confirm and create the parsed task |

---

## Required Environment Variables

| Variable | Purpose | Required |
|---|---|---|
| `OPENAI_API_KEY` | OpenAI API key for AI task capture | No (fallback to rule-based) |
| `AI_PROVIDER` | AI provider: `openai` or `rulebased` | No (default: `rulebased`) |
| `OPENAI_MODEL` | OpenAI model to use | No (default: `gpt-4o-mini`) |

---

## Delivery Steps

### ✓ Step 1–7: Original Milestones 1–7 (Complete)
All 7 original milestones are complete with 262 passing tests.

### Step 8: Database Migrations V6–V12
Create all 7 new Flyway migrations for the new schema.

- Create V6: Add scheduling fields to tasks table
- Create V7: User scheduling preferences table
- Create V8: Subtasks table
- Create V9: Task recurrence rules table
- Create V10: Labels and task_labels tables
- Create V11: Task dependencies table
- Create V12: Productivity goals table
- Update `Task.java` entity with new scheduling fields
- Run `mvn clean test` to verify migrations work with H2

### Step 9: Advanced Task Management — Subtasks, Labels, Dependencies
Implement the foundational task relationships.

- Create `Subtask` entity, repository, service, controller, DTOs
- Create `Label` entity, repository, service, controller, DTOs
- Create `TaskLabel` join table handling
- Create `TaskDependency` entity, repository, service, controller, DTOs
- Implement dependency cycle detection (DFS-based)
- Implement topological sort for execution order
- Implement parent task completion progress calculation
- Update `TaskSpecifications` for label filtering
- Update `TaskResponse` to include subtasks, labels, and dependencies
- Add comprehensive unit and integration tests
- Run `mvn clean test`

### Step 10: Advanced Task Management — Recurring Tasks
Implement lazy recurrence generation.

- Create `TaskRecurrenceRule` entity, repository, service, controller, DTOs
- Implement recurrence types: DAILY, WEEKDAYS, WEEKLY, MONTHLY, YEARLY, EVERY_N_DAYS, CUSTOM_WEEKLY
- Implement lazy next-occurrence generation
- Implement pause/resume functionality
- Create recurrence scheduler to check for due occurrences
- Add comprehensive unit and integration tests
- Run `mvn clean test`

### ✓ done Step 11: Smart Scheduling Engine
Implement the constraint-based scheduling algorithm.

- Create `UserSchedulingPreferences` entity, repository, service, controller, DTOs
- Create `SchedulingEngine` with topological sort and greedy scheduling
- Implement conflict detection
- Implement deadline risk calculation
- Create scheduling plan preview and apply endpoints
- Add comprehensive unit and integration tests
- Run `mvn clean test`

### ✓ done Step 12: Productivity Intelligence
Implement analytics, trends, insights, and goals.

- Create `ProductivityGoal` entity, repository, service, controller, DTOs
- Create `AnalyticsService` with efficient DB aggregation queries
- Implement dashboard overview, daily/weekly/monthly stats
- Implement trend calculations
- Implement rule-based insight generation
- Add comprehensive unit and integration tests
- Run `mvn clean test`

### ✓ done Step 13: AI-Powered Task Capture
Implement natural language task parsing.

- Create `TaskCaptureProvider` interface
- Implement `OpenAiTaskCaptureProvider` with OpenAI API integration
- Implement `RuleBasedTaskCaptureProvider` with deterministic parsing
- Create `TaskCaptureService` with parse and confirm endpoints
- Create `TaskCaptureController`
- Add timezone-aware date interpretation
- Add comprehensive unit and integration tests
- Run `mvn clean test`

### ✓ done Step 14: Integration, Documentation, and Final Verification
Final integration and documentation.

- Update `SecurityConfig` to permit new public endpoints if needed
- Update `ApplicationProperties` with new configuration records
- Update `application.properties` with new defaults
- Update `application-test.properties` for test environment
- Create `API_DOCUMENTATION_V2.md` with complete API reference
- Create `BACKEND_IMPLEMENTATION_REPORT.md` with architecture summary
- Update `README.md` with new features
- Final `mvn clean test` sweep
- Fix any regressions