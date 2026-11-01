# SmartTaskManager V2 — Complete API Reference

**Base URL:** `http://localhost:9999/api/v1`

**Authentication:** All endpoints except `/auth/**` require `Authorization: Bearer <accessToken>` header.

---

## Table of Contents

1. [Authentication](#1-authentication)
2. [Task Management](#2-task-management)
3. [Subtasks](#3-subtasks)
4. [Labels](#4-labels)
5. [Task Dependencies](#5-task-dependencies)
6. [Recurring Tasks](#6-recurring-tasks)
7. [Category Management](#7-category-management)
8. [Reminder Management](#8-reminder-management)
9. [Smart Scheduling](#9-smart-scheduling)
10. [Productivity Analytics](#10-productivity-analytics)
11. [AI Task Capture](#11-ai-task-capture)
12. [Error Responses](#12-error-responses)
13. [Example Workflows](#13-example-workflows)

---

## 1. Authentication

All auth endpoints are **public** — no JWT token required.

### 1.1 Register

**POST** `/auth/register`

Register a new user account. A verification email is sent to the provided address.

**Request Body:**
```json
{
  "fullName": "John Doe",
  "email": "john@example.com",
  "password": "SecureP@ss1"
}
```

**Password Requirements:**
- Minimum 8 characters
- At least one uppercase letter
- At least one lowercase letter
- At least one digit
- At least one special character

**Response (201 Created):**
```json
{
  "success": true,
  "message": "Registration successful. Please check your email to verify your account.",
  "data": null,
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

### 1.2 Verify Email

**POST** `/auth/verify-email`

Verify the user's email address using the token from the verification email.

**Request Body:**
```json
{
  "token": "abc123def456..."
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Email verified successfully.",
  "data": null,
  "timestamp": "2025-01-15T10:30:00Z"
}
```

**Alternative (GET link):** `GET /auth/verify-email?token=abc123def456...`

---

### 1.3 Resend Verification Email

**POST** `/auth/resend-verification`

Resend the verification email. Rate limited: 1 request per 2 minutes per email.

**Request Body:**
```json
{
  "email": "john@example.com"
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Verification email sent.",
  "data": null,
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

### 1.4 Login

**POST** `/auth/login`

Authenticate with email and password. Returns JWT access token and refresh token.

**Rate Limiting:** 5 attempts per 5 minutes per email.

**Request Body:**
```json
{
  "email": "john@example.com",
  "password": "SecureP@ss1"
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Login successful.",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "a1b2c3d4e5f6...",
    "accessTokenExpiresInMs": 900000
  },
  "timestamp": "2025-01-15T10:30:00Z"
}
```

**Notes:**
- `accessToken` expires in 15 minutes (configurable via `JWT_ACCESS_EXPIRATION_MS`)
- `refreshToken` expires in 7 days (configurable via `JWT_REFRESH_EXPIRATION_MS`)
- Unverified email returns 400 with `EMAIL_NOT_VERIFIED` error code

---

### 1.5 Refresh Access Token

**POST** `/auth/refresh`

Exchange a valid refresh token for a new access token. The old refresh token is rotated (invalidated).

**Request Body:**
```json
{
  "refreshToken": "a1b2c3d4e5f6..."
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Token refreshed successfully.",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "new_refresh_token...",
    "accessTokenExpiresInMs": 900000
  },
  "timestamp": "2025-01-15T10:30:00Z"
}
```

**Error Cases:**
- `TOKEN_REVOKED` (401): Token was already used or revoked
- `TOKEN_EXPIRED` (401): Refresh token has expired
- `INVALID_TOKEN` (401): Token does not exist

---

### 1.6 Logout

**POST** `/auth/logout`

Revoke the refresh token. The access token remains valid until natural expiration.

**Request Body:**
```json
{
  "refreshToken": "a1b2c3d4e5f6..."
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Logged out successfully.",
  "data": null,
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

### 1.7 Forgot Password

**POST** `/auth/forgot-password`

Request a password reset email. Returns the same response whether the email exists or not (anti-enumeration).

**Rate Limiting:** 3 requests per 10 minutes per email.

**Request Body:**
```json
{
  "email": "john@example.com"
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "message": "If an account exists for this email, a password reset link has been sent.",
  "data": null,
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

### 1.8 Reset Password

**POST** `/auth/reset-password`

Confirm password reset using the token from the email. All existing sessions are invalidated.

**Request Body:**
```json
{
  "token": "reset_token_abc123...",
  "newPassword": "NewSecureP@ss2"
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Password has been reset successfully. You may now log in with your new password.",
  "data": null,
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

## 2. Task Management

**Authentication Required:** Yes

### 2.1 Create Task

**POST** `/tasks`

**Request Body:**
```json
{
  "title": "Complete project proposal",
  "description": "Draft the Q1 project proposal",
  "status": "TODO",
  "priority": "HIGH",
  "dueDate": "2025-02-01T17:00:00Z",
  "categoryId": 1
}
```

**Response (201 Created):**
```json
{
  "success": true,
  "message": "Task created successfully.",
  "data": {
    "id": 1,
    "title": "Complete project proposal",
    "description": "Draft the Q1 project proposal",
    "status": "TODO",
    "priority": "HIGH",
    "dueDate": "2025-02-01T17:00:00Z",
    "completedAt": null,
    "createdAt": "2025-01-15T10:30:00Z",
    "updatedAt": "2025-01-15T10:30:00Z",
    "category": {
      "id": 1,
      "name": "Work"
    }
  },
  "timestamp": "2025-01-15T10:30:00Z"
}
```

**Validation:**
- `title`: Required, max 150 characters
- `description`: Optional, max 2000 characters
- `status`: Optional, defaults to `TODO`. Values: `TODO`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`
- `priority`: Optional, defaults to `MEDIUM`. Values: `LOW`, `MEDIUM`, `HIGH`, `URGENT`
- `dueDate`: Optional ISO 8601 timestamp
- `categoryId`: Optional, must belong to the authenticated user

---

### 2.2 List Tasks (with Filtering, Pagination, Sorting)

**GET** `/tasks`

**Query Parameters:**

| Parameter | Type | Default | Description |
|---|---|---|---|
| `status` | String | — | Filter by status: `TODO`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED` |
| `priority` | String | — | Filter by priority: `LOW`, `MEDIUM`, `HIGH`, `URGENT` |
| `categoryId` | Long | — | Filter by category ID |
| `search` | String | — | Free-text search in title and description |
| `page` | Integer | `0` | Page number (0-indexed) |
| `size` | Integer | `20` | Page size (max 100) |
| `sortBy` | String | `createdAt` | Sort field: `createdAt`, `updatedAt`, `title`, `status`, `priority`, `dueDate` |
| `sortDirection` | String | `DESC` | Sort direction: `ASC` or `DESC` |

**Example:** `GET /tasks?status=TODO&priority=HIGH&page=0&size=10&sortBy=dueDate&sortDirection=ASC`

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Tasks retrieved successfully.",
  "data": {
    "content": [ /* TaskResponse array */ ],
    "totalElements": 5,
    "totalPages": 1,
    "size": 10,
    "number": 0,
    "first": true,
    "last": true
  },
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

### 2.3 Get Task by ID

**GET** `/tasks/{id}`

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Task retrieved successfully.",
  "data": {
    "id": 1,
    "title": "Complete project proposal",
    "description": "Draft the Q1 project proposal",
    "status": "TODO",
    "priority": "HIGH",
    "dueDate": "2025-02-01T17:00:00Z",
    "completedAt": null,
    "createdAt": "2025-01-15T10:30:00Z",
    "updatedAt": "2025-01-15T10:30:00Z",
    "category": {
      "id": 1,
      "name": "Work"
    }
  },
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

### 2.4 Update Task

**PUT** `/tasks/{id}`

Partial update — only provided fields are changed.

**Request Body:**
```json
{
  "title": "Updated title",
  "status": "IN_PROGRESS"
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Task updated successfully.",
  "data": { /* TaskResponse */ },
  "timestamp": "2025-01-15T10:30:00Z"
}
```

**Notes:**
- `completedAt` is auto-set when status changes to `COMPLETED`
- `completedAt` is cleared when status changes from `COMPLETED` to another status

---

### 2.5 Delete Task

**DELETE** `/tasks/{id}`

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Task deleted successfully.",
  "data": null,
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

### 2.6 Task Statistics

**GET** `/tasks/statistics`

Returns aggregated statistics for the authenticated user.

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Task statistics retrieved successfully.",
  "data": {
    "totalTasks": 15,
    "completedTasks": 5,
    "pendingTasks": 10,
    "overdueTasks": 2,
    "byStatus": {
      "TODO": 5,
      "IN_PROGRESS": 3,
      "COMPLETED": 5,
      "CANCELLED": 2
    },
    "byPriority": {
      "LOW": 4,
      "MEDIUM": 6,
      "HIGH": 4,
      "URGENT": 1
    }
  },
  "timestamp": "2025-01-15T10:30:00Z"
}
```

**Overdue Definition:** Tasks with due date in the past and status not `COMPLETED` or `CANCELLED`.

---

## 3. Subtasks

**Authentication Required:** Yes

### 3.1 Create Subtask

**POST** `/tasks/{taskId}/subtasks`

**Request Body:**
```json
{
  "title": "Research competitors",
  "description": "Analyze top 5 competitors",
  "priority": "HIGH",
  "dueDate": "2025-01-20T17:00:00Z"
}
```

**Response (201 Created):**
```json
{
  "success": true,
  "message": "Subtask created successfully.",
  "data": {
    "id": 1,
    "parentTaskId": 1,
    "title": "Research competitors",
    "description": "Analyze top 5 competitors",
    "status": "TODO",
    "priority": "HIGH",
    "dueDate": "2025-01-20T17:00:00Z",
    "completedAt": null,
    "position": 0,
    "createdAt": "2025-01-15T10:30:00Z",
    "updatedAt": "2025-01-15T10:30:00Z"
  },
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

### 3.2 List Subtasks

**GET** `/tasks/{taskId}/subtasks`

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Subtasks retrieved successfully.",
  "data": [ /* SubtaskResponse array */ ],
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

### 3.3 Update Subtask

**PUT** `/tasks/{taskId}/subtasks/{subtaskId}`

**Request Body:**
```json
{
  "title": "Updated subtask title",
  "status": "COMPLETED"
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Subtask updated successfully.",
  "data": { /* SubtaskResponse */ },
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

### 3.4 Delete Subtask

**DELETE** `/tasks/{taskId}/subtasks/{subtaskId}`

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Subtask deleted successfully.",
  "data": null,
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

### 3.5 Parent Task Completion Progress

When listing subtasks, the parent task's completion progress is calculated as:

```
progress = (completedSubtasks / totalSubtasks) * 100
```

---

## 4. Labels

**Authentication Required:** Yes

### 4.1 Create Label

**POST** `/labels`

**Request Body:**
```json
{
  "name": "Work",
  "color": "#FF5733"
}
```

**Response (201 Created):**
```json
{
  "success": true,
  "message": "Label created successfully.",
  "data": {
    "id": 1,
    "name": "Work",
    "color": "#FF5733"
  },
  "timestamp": "2025-01-15T10:30:00Z"
}
```

**Validation:**
- `name`: Required, unique per user (case-insensitive)
- `color`: Optional, hex color code

---

### 4.2 List Labels

**GET** `/labels`

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Labels retrieved successfully.",
  "data": [
    {
      "id": 1,
      "name": "Work",
      "color": "#FF5733"
    },
    {
      "id": 2,
      "name": "Personal",
      "color": "#33FF57"
    }
  ],
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

### 4.3 Update Label

**PUT** `/labels/{labelId}`

**Request Body:**
```json
{
  "name": "Updated Name",
  "color": "#FF0000"
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Label updated successfully.",
  "data": { /* LabelResponse */ },
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

### 4.4 Delete Label

**DELETE** `/labels/{labelId}`

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Label deleted successfully.",
  "data": null,
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

### 4.5 Assign Label to Task

**POST** `/tasks/{taskId}/labels/{labelId}`

Assigns a label to a task. Both the task and label must belong to the authenticated user.

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Label assigned to task successfully.",
  "data": null,
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

### 4.6 Remove Label from Task

**DELETE** `/tasks/{taskId}/labels/{labelId}`

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Label removed from task successfully.",
  "data": null,
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

## 5. Task Dependencies

**Authentication Required:** Yes

### 5.1 Add Dependency

**POST** `/tasks/{taskId}/dependencies/{dependencyTaskId}`

Marks `dependencyTaskId` as a prerequisite for `taskId`. Both tasks must belong to the authenticated user.

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Dependency added successfully.",
  "data": null,
  "timestamp": "2025-01-15T10:30:00Z"
}
```

**Validation:**
- Self-dependency is rejected (400 `BUSINESS_RULE_VIOLATION`)
- Circular dependencies are detected and rejected (400 `BUSINESS_RULE_VIOLATION`)
- Cross-user dependencies are rejected (404 `RESOURCE_NOT_FOUND`)

---

### 5.2 Remove Dependency

**DELETE** `/tasks/{taskId}/dependencies/{dependencyTaskId}`

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Dependency removed successfully.",
  "data": null,
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

### 5.3 List Dependencies

**GET** `/tasks/{taskId}/dependencies`

Returns tasks that `taskId` depends on (prerequisites).

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Dependencies retrieved successfully.",
  "data": [ /* Task array */ ],
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

### 5.4 List Dependents

**GET** `/tasks/{taskId}/dependents`

Returns tasks that depend on `taskId` (blocked by this task).

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Dependents retrieved successfully.",
  "data": [ /* Task array */ ],
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

### 5.5 Dependency Status

**GET** `/tasks/{taskId}/dependency-status`

Returns whether all dependencies for `taskId` are completed.

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Dependency status retrieved successfully.",
  "data": {
    "taskId": 1,
    "allDependenciesCompleted": true,
    "totalDependencies": 3,
    "completedDependencies": 3,
    "pendingDependencies": 0
  },
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

## 6. Recurring Tasks

**Authentication Required:** Yes

### 6.1 Create Recurrence Rule

**POST** `/tasks/{taskId}/recurrence`

**Request Body:**
```json
{
  "recurrenceType": "WEEKLY",
  "recurrenceInterval": 1,
  "weekDays": "MONDAY,FRIDAY",
  "occurrenceCount": 10,
  "endDate": "2025-12-31T23:59:59Z"
}
```

**Response (201 Created):**
```json
{
  "success": true,
  "message": "Recurrence rule created successfully.",
  "data": {
    "id": 1,
    "taskId": 1,
    "recurrenceType": "WEEKLY",
    "recurrenceInterval": 1,
    "weekDays": "MONDAY,FRIDAY",
    "monthDay": null,
    "occurrenceCount": 10,
    "generatedOccurrences": 0,
    "endDate": "2025-12-31T23:59:59Z",
    "isPaused": false,
    "isCompleted": false,
    "nextOccurrenceAt": "2025-01-22T10:00:00Z"
  },
  "timestamp": "2025-01-15T10:30:00Z"
}
```

**Recurrence Types:**
- `DAILY` — Every day
- `WEEKDAYS` — Monday through Friday
- `WEEKLY` — Every N weeks
- `MONTHLY` — Same day each month
- `YEARLY` — Same date each year
- `EVERY_N_DAYS` — Every N days
- `CUSTOM_WEEKLY` — Selected days of the week

**Notes:**
- Only one recurrence rule per task
- Lazy occurrence generation — next occurrence is calculated on-demand
- Timezone-aware using the task's configured timezone

---

### 6.2 Get Recurrence Rule

**GET** `/tasks/{taskId}/recurrence`

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Recurrence rule retrieved successfully.",
  "data": { /* RecurrenceResponse */ },
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

### 6.3 Update Recurrence Rule

**PUT** `/tasks/{taskId}/recurrence`

**Request Body:**
```json
{
  "recurrenceType": "DAILY",
  "recurrenceInterval": 1,
  "occurrenceCount": 5
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Recurrence rule updated successfully.",
  "data": { /* RecurrenceResponse */ },
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

### 6.4 Delete Recurrence Rule

**DELETE** `/tasks/{taskId}/recurrence`

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Recurrence rule deleted successfully.",
  "data": null,
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

### 6.5 Pause Recurrence

**POST** `/tasks/{taskId}/recurrence/pause`

Pauses the recurrence rule without deleting it. No new occurrences will be generated.

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Recurrence paused successfully.",
  "data": { /* RecurrenceResponse with isPaused=true */ },
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

### 6.6 Resume Recurrence

**POST** `/tasks/{taskId}/recurrence/resume`

Resumes a paused recurrence rule.

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Recurrence resumed successfully.",
  "data": { /* RecurrenceResponse with isPaused=false */ },
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

## 7. Category Management

**Authentication Required:** Yes

### 7.1 Create Category

**POST** `/categories`

**Request Body:**
```json
{
  "name": "Work",
  "description": "Work-related tasks",
  "color": "#FF5733"
}
```

**Response (201 Created):**
```json
{
  "success": true,
  "message": "Category created successfully.",
  "data": {
    "id": 1,
    "name": "Work",
    "description": "Work-related tasks",
    "color": "#FF5733"
  },
  "timestamp": "2025-01-15T10:30:00Z"
}
```

**Validation:**
- `name`: Required, max 50 characters, unique per user (case-insensitive)
- `description`: Optional
- `color`: Optional, hex color code

---

### 7.2 List Categories

**GET** `/categories`

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Categories retrieved successfully.",
  "data": [ /* CategoryResponse array */ ],
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

### 7.3 Get Category by ID

**GET** `/categories/{id}`

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Category retrieved successfully.",
  "data": { /* CategoryResponse */ },
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

### 7.4 Update Category

**PUT** `/categories/{id}`

**Request Body:**
```json
{
  "name": "Updated Name",
  "description": "Updated description"
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Category updated successfully.",
  "data": { /* CategoryResponse */ },
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

### 7.5 Delete Category

**DELETE** `/categories/{id}`

Deletes the category. Associated tasks have their `category_id` set to `NULL` (tasks are not deleted).

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Category deleted successfully.",
  "data": null,
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

## 8. Reminder Management

**Authentication Required:** Yes

### 8.1 Create Reminder

**POST** `/reminders`

**Request Body:**
```json
{
  "taskId": 1,
  "scheduledAt": "2025-01-20T09:00:00Z"
}
```

**Response (201 Created):**
```json
{
  "success": true,
  "message": "Reminder created successfully.",
  "data": {
    "id": 1,
    "taskId": 1,
    "taskTitle": "Complete project proposal",
    "scheduledAt": "2025-01-20T09:00:00Z",
    "status": "PENDING",
    "sentAt": null,
    "retryCount": 0,
    "createdAt": "2025-01-15T10:30:00Z"
  },
  "timestamp": "2025-01-15T10:30:00Z"
}
```

**Validation:**
- `taskId`: Required, must belong to the authenticated user
- `scheduledAt`: Required, must be in the future

---

### 8.2 List Reminders

**GET** `/reminders`

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Reminders retrieved successfully.",
  "data": [ /* ReminderResponse array */ ],
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

### 8.3 Get Reminder by ID

**GET** `/reminders/{id}`

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Reminder retrieved successfully.",
  "data": { /* ReminderResponse */ },
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

### 8.4 Update Reminder

**PUT** `/reminders/{id}`

**Request Body:**
```json
{
  "scheduledAt": "2025-01-21T09:00:00Z",
  "status": "CANCELLED"
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Reminder updated successfully.",
  "data": { /* ReminderResponse */ },
  "timestamp": "2025-01-15T10:30:00Z"
}
```

**Notes:**
- Both fields are optional for partial updates
- Status cannot be changed for `SENT` or `CANCELLED` reminders

---

### 8.5 Delete Reminder

**DELETE** `/reminders/{id}`

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Reminder deleted successfully.",
  "data": null,
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

### 8.6 Reminder Scheduler

The background scheduler runs every 30 seconds (configurable) and processes due `PENDING` reminders:

1. Finds reminders where `scheduledAt <= now` and `status = PENDING`
2. Sends email to the task owner
3. Marks as `SENT` on success, increments `retryCount` on failure
4. After 3 failed retries, marks as `FAILED`

**Status Values:** `PENDING`, `SENT`, `FAILED`

---

## 9. Smart Scheduling

**Authentication Required:** Yes

### 9.1 Get Scheduling Preferences

**GET** `/scheduling/preferences`

Returns the user's working hours, timezone, and scheduling defaults. Creates defaults if none exist.

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Scheduling preferences retrieved.",
  "data": {
    "workDayStartTime": "09:00:00",
    "workDayEndTime": "17:00:00",
    "workDaysOfWeek": "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY",
    "defaultTaskDurationMinutes": 60,
    "breakDurationMinutes": 15,
    "timezone": "UTC",
    "workingMinutesPerDay": 435
  },
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

### 9.2 Update Scheduling Preferences

**PUT** `/scheduling/preferences`

**Request Body:**
```json
{
  "workDayStartTime": "08:00:00",
  "workDayEndTime": "18:00:00",
  "workDaysOfWeek": "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY",
  "defaultTaskDurationMinutes": 45,
  "breakDurationMinutes": 10,
  "timezone": "Asia/Kolkata"
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Scheduling preferences updated.",
  "data": { /* SchedulingPreferencesResponse */ },
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

### 9.3 Generate Schedule Plan

**POST** `/scheduling/plan`

Generates a proposed schedule based on priorities, deadlines, dependencies, and working hours. Does NOT modify the database.

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Schedule plan generated.",
  "data": {
    "scheduledTasks": [
      {
        "taskId": 1,
        "title": "Complete proposal",
        "scheduledStartAt": "2025-01-16T09:00:00Z",
        "scheduledEndAt": "2025-01-16T10:00:00Z",
        "reason": "High priority, deadline approaching"
      }
    ],
    "conflicts": [],
    "deadlineRisks": [],
    "unschedulableTaskIds": []
  },
  "timestamp": "2025-01-15T10:30:00Z"
}
```

**Algorithm:**
1. Retrieves incomplete tasks
2. Scores by priority weight × urgency factor
3. Topologically sorts for dependency order
4. Greedily schedules within working hours
5. Detects conflicts and deadline risks

---

### 9.4 Get Current Schedule Plan

**GET** `/scheduling/plan`

Returns the most recently generated schedule plan.

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Current schedule plan retrieved.",
  "data": { /* SchedulingPlanResponse */ },
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

### 9.5 Apply Schedule Plan

**POST** `/scheduling/plan/apply`

Applies the proposed schedule to tasks, updating `scheduledStartAt`, `scheduledEndAt`, and `schedulingStatus`. Requires explicit user action.

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Schedule plan applied.",
  "data": { /* SchedulingPlanResponse */ },
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

### 9.6 Get Scheduling Conflicts

**GET** `/scheduling/conflicts`

Returns overlapping scheduled tasks.

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Scheduling conflicts retrieved.",
  "data": [
    {
      "task1Id": 1,
      "task1Title": "Task A",
      "task2Id": 2,
      "task2Title": "Task B",
      "overlapStart": "2025-01-16T09:00:00Z",
      "overlapEnd": "2025-01-16T10:00:00Z"
    }
  ],
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

### 9.7 Get Deadline Risks

**GET** `/scheduling/deadline-risks`

Returns tasks at risk of missing their deadlines.

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Deadline risks retrieved.",
  "data": [
    {
      "taskId": 1,
      "title": "Complete proposal",
      "deadline": "2025-01-17T17:00:00Z",
      "estimatedDurationMinutes": 120,
      "availableWorkingMinutes": 60,
      "riskLevel": "HIGH"
    }
  ],
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

## 10. Productivity Analytics

**Authentication Required:** Yes

### 10.1 Dashboard Overview

**GET** `/analytics/overview`

Returns a summary of the user's current productivity.

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Dashboard overview retrieved.",
  "data": {
    "totalTasks": 25,
    "completedTasks": 10,
    "pendingTasks": 12,
    "inProgressTasks": 3,
    "overdueTasks": 2,
    "completionPercentage": 40.0,
    "completedToday": 3,
    "completedThisWeek": 8,
    "completedThisMonth": 10
  },
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

### 10.2 Daily Completion Stats

**GET** `/analytics/daily?startDate=2025-01-01&endDate=2025-01-31`

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Daily stats retrieved.",
  "data": [
    {
      "date": "2025-01-15",
      "completedCount": 3
    },
    {
      "date": "2025-01-16",
      "completedCount": 5
    }
  ],
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

### 10.3 Weekly Completion Stats

**GET** `/analytics/weekly?startDate=2025-01-01&endDate=2025-01-31`

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Weekly stats retrieved.",
  "data": [
    {
      "weekNumber": 3,
      "year": 2025,
      "completedCount": 12
    }
  ],
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

### 10.4 Monthly Completion Stats

**GET** `/analytics/monthly?startDate=2025-01-01&endDate=2025-12-31`

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Monthly stats retrieved.",
  "data": [
    {
      "month": 1,
      "year": 2025,
      "completedCount": 45
    }
  ],
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

### 10.5 Trends

**GET** `/analytics/trends?startDate=2025-01-01&endDate=2025-01-31`

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Trends retrieved.",
  "data": {
    "completionRate": 40.0,
    "averageCompletionTimeMinutes": 120,
    "trendDirection": "IMPROVING",
    "weeklyComparison": {
      "currentWeekCompleted": 8,
      "previousWeekCompleted": 5
    }
  },
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

### 10.6 Distribution

**GET** `/analytics/distribution`

Returns task distribution by priority and category.

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Distribution retrieved.",
  "data": {
    "byPriority": {
      "LOW": 5,
      "MEDIUM": 10,
      "HIGH": 8,
      "URGENT": 2
    },
    "byCategory": {
      "Work": 15,
      "Personal": 10
    }
  },
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

### 10.7 Insights

**GET** `/analytics/insights`

Returns rule-based productivity insights grounded in actual data.

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Insights retrieved.",
  "data": {
    "insights": [
      {
        "message": "You have 2 overdue tasks that need attention.",
        "type": "WARNING"
      },
      {
        "message": "You completed 8 tasks this week, up from 5 last week.",
        "type": "POSITIVE"
      },
      {
        "message": "Your high-priority tasks are accumulating: 8 pending.",
        "type": "WARNING"
      }
    ]
  },
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

### 10.8 Create Productivity Goal

**POST** `/analytics/goals`

**Request Body:**
```json
{
  "goalType": "WEEKLY",
  "targetCount": 10,
  "periodStartDate": "2025-01-13",
  "periodEndDate": "2025-01-19"
}
```

**Response (201 Created):**
```json
{
  "success": true,
  "message": "Goal created successfully.",
  "data": {
    "id": 1,
    "goalType": "WEEKLY",
    "targetCount": 10,
    "currentCount": 0,
    "periodStartDate": "2025-01-13",
    "periodEndDate": "2025-01-19",
    "isActive": true
  },
  "timestamp": "2025-01-15T10:30:00Z"
}
```

**Goal Types:** `DAILY`, `WEEKLY`, `MONTHLY`

---

### 10.9 List Goals

**GET** `/analytics/goals`

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Goals retrieved successfully.",
  "data": [ /* GoalResponse array */ ],
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

### 10.10 Update Goal

**PUT** `/analytics/goals/{goalId}`

**Request Body:**
```json
{
  "targetCount": 15,
  "periodEndDate": "2025-01-26"
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Goal updated successfully.",
  "data": { /* GoalResponse */ },
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

### 10.11 Delete Goal

**DELETE** `/analytics/goals/{goalId}`

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Goal deleted successfully.",
  "data": null,
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

## 11. AI Task Capture

**Authentication Required:** Yes

### 11.1 Parse Natural Language

**POST** `/task-capture/parse`

Parses natural language input into structured task data. Returns a preview — **no task is created**.

**Request Body:**
```json
{
  "text": "Prepare for Java interview tomorrow at 7 PM, high priority"
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Task parsed successfully.",
  "data": {
    "title": "Prepare for Java interview",
    "description": null,
    "status": "TODO",
    "priority": "HIGH",
    "dueDate": "2025-01-16T19:00:00Z",
    "categoryName": null,
    "estimatedDurationMinutes": null,
    "recurrenceType": null,
    "recurrenceInterval": null,
    "ambiguities": "Time interpreted as 19:00 in UTC timezone",
    "provider": "rulebased"
  },
  "timestamp": "2025-01-15T10:30:00Z"
}
```

**Supported Patterns (Rule-based provider):**
- Dates: "tomorrow", "next Monday", "in 3 days", "Jan 15"
- Times: "at 7 PM", "at 14:00"
- Priorities: "high priority", "urgent", "low priority"
- Duration: "30 minutes", "2 hours"
- Recurrence: "every day", "every week", "every 2 days"
- Categories: "in Work category", "category: Personal"

**Provider Field:**
- `"rulebased"` — Deterministic regex-based parser (always available)
- `"openai"` — OpenAI GPT model (requires `OPENAI_API_KEY` env var)

---

### 11.2 Confirm and Create Task

**POST** `/task-capture/confirm`

Creates a task from the parsed data. User can modify the parsed values before confirmation.

**Request Body:**
```json
{
  "title": "Prepare for Java interview",
  "description": "Review core Java concepts",
  "status": "TODO",
  "priority": "HIGH",
  "dueDate": "2025-01-16T19:00:00Z",
  "categoryName": "Work",
  "estimatedDurationMinutes": 120,
  "recurrenceType": null,
  "recurrenceInterval": null
}
```

**Response (201 Created):**
```json
{
  "success": true,
  "message": "Task created successfully.",
  "data": {
    "id": 1,
    "title": "Prepare for Java interview",
    "description": "Review core Java concepts",
    "status": "TODO",
    "priority": "HIGH",
    "dueDate": "2025-01-16T19:00:00Z",
    "completedAt": null,
    "createdAt": "2025-01-15T10:30:00Z",
    "updatedAt": "2025-01-15T10:30:00Z",
    "category": {
      "id": 1,
      "name": "Work"
    }
  },
  "timestamp": "2025-01-15T10:30:00Z"
}
```

**Notes:**
- If `categoryName` is provided and doesn't exist, it is created automatically
- If `recurrenceType` is provided, a recurrence rule is created after task creation
- Title is required; all other fields are optional

---

## 12. Error Responses

All errors return a consistent JSON structure:

### 12.1 Validation Error (400)

```json
{
  "timestamp": "2025-01-15T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "errorCode": "VALIDATION_FAILED",
  "message": "Input validation failed for one or more fields",
  "path": "/api/v1/tasks",
  "validationErrors": [
    {
      "field": "title",
      "rejectedValue": "",
      "message": "Title must not be blank"
    }
  ]
}
```

### 12.2 Authentication Required (401)

```json
{
  "timestamp": "2025-01-15T10:30:00Z",
  "status": 401,
  "error": "Unauthorized",
  "errorCode": "AUTHENTICATION_REQUIRED",
  "message": "Authentication is required to access this resource",
  "path": "/api/v1/tasks"
}
```

### 12.3 Access Denied (403)

```json
{
  "timestamp": "2025-01-15T10:30:00Z",
  "status": 403,
  "error": "Forbidden",
  "errorCode": "ACCESS_DENIED",
  "message": "You do not have permission to access this resource",
  "path": "/api/v1/tasks"
}
```

### 12.4 Resource Not Found (404)

```json
{
  "timestamp": "2025-01-15T10:30:00Z",
  "status": 404,
  "error": "Not Found",
  "errorCode": "RESOURCE_NOT_FOUND",
  "message": "Task not found",
  "path": "/api/v1/tasks/999"
}
```

### 12.5 Duplicate Resource (409)

```json
{
  "timestamp": "2025-01-15T10:30:00Z",
  "status": 409,
  "error": "Conflict",
  "errorCode": "DUPLICATE_RESOURCE",
  "message": "Category with name 'Work' already exists",
  "path": "/api/v1/categories"
}
```

### 12.6 Business Rule Violation (400)

```json
{
  "timestamp": "2025-01-15T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "errorCode": "BUSINESS_RULE_VIOLATION",
  "message": "Circular dependency detected",
  "path": "/api/v1/tasks/1/dependencies/2"
}
```

### 12.7 Token Errors (401)

```json
{
  "timestamp": "2025-01-15T10:30:00Z",
  "status": 401,
  "error": "Unauthorized",
  "errorCode": "TOKEN_EXPIRED",
  "message": "Refresh token has expired",
  "path": "/api/v1/auth/refresh"
}
```

**Token Error Codes:**
- `TOKEN_EXPIRED` — Token has expired
- `TOKEN_REVOKED` — Token was already used or revoked
- `INVALID_TOKEN` — Token does not exist or is invalid

### 12.8 Rate Limit Exceeded (429)

```json
{
  "timestamp": "2025-01-15T10:30:00Z",
  "status": 429,
  "error": "Too Many Requests",
  "errorCode": "RATE_LIMIT_EXCEEDED",
  "message": "Too many login attempts. Please try again later.",
  "path": "/api/v1/auth/login"
}
```

### 12.9 Server Error (500)

```json
{
  "timestamp": "2025-01-15T10:30:00Z",
  "status": 500,
  "error": "Internal Server Error",
  "errorCode": "INTERNAL_ERROR",
  "message": "An unexpected error occurred",
  "path": "/api/v1/tasks"
}
```

---

## 13. Example Workflows

### 13.1 Complete Registration and Login Flow

```bash
# 1. Register
curl -X POST http://localhost:9999/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"fullName":"John Doe","email":"john@example.com","password":"SecureP@ss1"}'

# 2. Verify email (using token from email)
curl -X POST http://localhost:9999/api/v1/auth/verify-email \
  -H "Content-Type: application/json" \
  -d '{"token":"abc123..."}'

# 3. Login
curl -X POST http://localhost:9999/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"john@example.com","password":"SecureP@ss1"}'

# Store the accessToken and refreshToken from the response
```

### 13.2 Create Task with Category and Reminder

```bash
# 1. Create category
curl -X POST http://localhost:9999/api/v1/categories \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"name":"Work","color":"#FF5733"}'

# 2. Create task with category
curl -X POST http://localhost:9999/api/v1/tasks \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"title":"Complete proposal","priority":"HIGH","dueDate":"2025-02-01T17:00:00Z","categoryId":1}'

# 3. Create reminder for the task
curl -X POST http://localhost:9999/api/v1/reminders \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"taskId":1,"scheduledAt":"2025-01-31T09:00:00Z"}'
```

### 13.3 AI Task Capture Flow

```bash
# 1. Parse natural language
curl -X POST http://localhost:9999/api/v1/task-capture/parse \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"text":"Prepare for Java interview tomorrow at 7 PM, high priority"}'

# 2. Review the preview and confirm
curl -X POST http://localhost:9999/api/v1/task-capture/confirm \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"title":"Prepare for Java interview","priority":"HIGH","dueDate":"2025-01-16T19:00:00Z"}'
```

### 13.4 Token Refresh Flow

```bash
# When access token expires (401 response), refresh it:
curl -X POST http://localhost:9999/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<storedRefreshToken>"}'

# Store the new accessToken and refreshToken
# If TOKEN_REVOKED error, redirect to login
```

---

## OpenAPI / Swagger UI

- **Swagger UI:** `http://localhost:9999/swagger-ui.html`
- **OpenAPI JSON:** `http://localhost:9999/v3/api-docs`

To authenticate in Swagger UI:
1. Click the "Authorize" button
2. Enter `Bearer <accessToken>`
3. Click "Authorize"
4. Click "Close"

---

## Health Check

**GET** `/actuator/health` (Public — no authentication required)

```json
{
  "status": "UP"
}
```
