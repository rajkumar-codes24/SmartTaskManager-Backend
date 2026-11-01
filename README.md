# Smart Task Manager — Production-Grade Backend

Smart Task Manager is a production-quality, secure backend built with **Java 21 LTS**, **Spring Boot 4.1.2**, **Spring Security 7**, **Spring Data JPA**, **Hibernate ORM 7**, **Flyway**, and **MySQL**.

---

## Architecture Overview

The system is designed as a **Layered Modular Monolith**:
- **Controllers (`com.smarttaskmanager.*.controller`)**: Handle HTTP requests, DTO mapping, and Bean Validation.
- **Services (`com.smarttaskmanager.*.service`)**: Contain business logic, anti-IDOR resource ownership enforcement, and transaction boundaries.
- **Repositories (`com.smarttaskmanager.*.repository`)**: Encapsulate persistence logic via Spring Data JPA and optimized JPQL projections.
- **Entities (`com.smarttaskmanager.*.entity`)**: Represent database tables mapped with proper constraints, relationships, and auditing timestamps (`BaseAuditableEntity`).
- **Migrations (`src/main/resources/db/migration/`)**: Versioned Flyway SQL scripts executed automatically on startup.
- **Security (`com.smarttaskmanager.security`)**: Stateless JWT authentication, BCrypt password hashing, and role/ownership authorization.

---

## Prerequisites

- **Java JDK**: 21 LTS or newer.
- **Apache Maven**: 3.9+ installed and added to your `PATH`.
- **MySQL Server**: 8.0 or newer.

---

## Database Setup & Automated Schema Creation

You **do not** need to manually run `CREATE TABLE` scripts. Flyway automatically creates all required tables, constraints, foreign keys, and indexes on application startup.

### 1. Create an Empty MySQL Database
Open your MySQL CLI (`mysql -u root -p`) or GUI (MySQL Workbench, DBeaver) and run:

```sql
CREATE DATABASE smart_task_manager CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. Configure Environment Variables
Set the following environment variables in your terminal or IDE run configuration:

| Variable | Description | Example Value |
| :--- | :--- | :--- |
| `DB_URL` | JDBC connection URL to MySQL | `jdbc:mysql://localhost:3306/smart_task_manager?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC` |
| `DB_USERNAME` | Your MySQL username | `root` |
| `DB_PASSWORD` | Your MySQL password | `your_secret_password` |
| `JWT_SECRET` | 256-bit or 512-bit hex signing key | Generate with `openssl rand -hex 64` |
| `SMTP_HOST` | SMTP server host | `smtp.mailtrap.io` |
| `SMTP_PORT` | SMTP port | `2525` (or `587`) |
| `SMTP_USERNAME` | SMTP username | *(from your email provider)* |
| `SMTP_PASSWORD` | SMTP password | *(from your email provider)* |
| `MAIL_FROM` | Sender email address | `noreply@smarttaskmanager.com` |

#### Generating a Strong JWT Secret
Run the following in bash or PowerShell:
```bash
openssl rand -hex 64
```
Export it before starting the backend:
```powershell
$env:JWT_SECRET="your_generated_64_byte_hex_string"
```

---

## Running the Application

### Build and Test
```powershell
mvn clean test
```

### Run Locally
```powershell
mvn spring-boot:run
```

Once started:
1. **Flyway** checks your MySQL `smart_task_manager` database and executes migrations `V1` through `V5`.
2. **Hibernate** runs `ddl-auto=validate` to verify that all JPA entity definitions precisely match the generated MySQL tables.
3. **Actuator Health**: `http://localhost:9999/actuator/health`
4. **Swagger UI**: `http://localhost:9999/swagger-ui.html`
5. **OpenAPI JSON**: `http://localhost:9999/v3/api-docs`

---

## Verifying Database Migrations in MySQL

After starting the application, connect to MySQL and inspect the created schema:

```sql
USE smart_task_manager;
SHOW TABLES;
```

You should see 8 tables:
- `flyway_schema_history`
- `users`
- `refresh_tokens`
- `email_verification_tokens`
- `password_reset_tokens`
- `categories`
- `tasks`
- `reminders`

To view Flyway's execution record:
```sql
SELECT installed_rank, version, description, type, script, success FROM flyway_schema_history;
```

---

## API Reference

All API endpoints require authentication via JWT Bearer token, except for the `/api/v1/auth/**` endpoints.

Include the token in every request:
```
Authorization: Bearer <your_access_token>
```

Obtain a token by calling `POST /api/v1/auth/login` with valid credentials.

### Authentication Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/auth/register` | Register a new user |
| POST | `/api/v1/auth/login` | Login and receive JWT tokens |
| POST | `/api/v1/auth/refresh` | Refresh access and refresh tokens |
| POST | `/api/v1/auth/logout` | Logout and revoke refresh token |
| POST | `/api/v1/auth/verify-email` | Verify email address |
| POST | `/api/v1/auth/resend-verification` | Resend verification email |
| POST | `/api/v1/auth/forgot-password` | Request password reset |
| POST | `/api/v1/auth/reset-password` | Confirm password reset |

### Category Management Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/categories` | Create a new category |
| GET | `/api/v1/categories` | List all categories for the authenticated user |
| GET | `/api/v1/categories/{id}` | Get a category by ID |
| PUT | `/api/v1/categories/{id}` | Update a category |
| DELETE | `/api/v1/categories/{id}` | Delete a category |

#### Create a Category

```
POST /api/v1/categories
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "Work",
  "description": "Work-related tasks",
  "color": "#5469D4"
}
```

**Response (201 CREATED):**
```json
{
  "success": true,
  "message": "Category created successfully.",
  "data": {
    "id": 1,
    "name": "Work",
    "description": "Work-related tasks",
    "color": "#5469D4"
  },
  "timestamp": "2026-10-29T18:00:00.000Z"
}
```

**Error (409 Conflict) — Duplicate name:**
```json
{
  "status": 409,
  "error": "Conflict",
  "errorCode": "DUPLICATE_RESOURCE",
  "message": "Category already exists with name: 'Work'",
  "path": "/api/v1/categories"
}
```

#### List Categories

```
GET /api/v1/categories
Authorization: Bearer <token>
```

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Categories retrieved successfully.",
  "data": [
    {
      "id": 1,
      "name": "Personal",
      "description": "Personal tasks",
      "color": "#FF0000"
    },
    {
      "id": 2,
      "name": "Work",
      "description": "Work-related tasks",
      "color": "#5469D4"
    }
  ],
  "timestamp": "2026-10-29T18:00:00.000Z"
}
```

#### Get Category by ID

```
GET /api/v1/categories/1
Authorization: Bearer <token>
```

**Response (200 OK):** Same structure as Create, with the category data.

**Error (404 Not Found) — Category not found or belongs to another user:**
```json
{
  "status": 404,
  "error": "Not Found",
  "errorCode": "RESOURCE_NOT_FOUND",
  "message": "Category not found with id: '1'",
  "path": "/api/v1/categories/1"
}
```

#### Update a Category

```
PUT /api/v1/categories/1
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "Updated Work",
  "description": "Updated description"
}
```

**Response (200 OK):** Returns the updated category. All fields are optional; only provided fields are updated.

#### Delete a Category

```
DELETE /api/v1/categories/1
Authorization: Bearer <token>
```

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Category deleted successfully.",
  "timestamp": "2026-10-29T18:00:00.000Z"
}
```

> **Note:** When a category is deleted, all tasks associated with it have their category reference set to `NULL`. Tasks are not deleted.

### Task Management Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/tasks` | Create a new task |
| GET | `/api/v1/tasks` | List tasks with filtering, pagination, and sorting |
| GET | `/api/v1/tasks/{id}` | Get a task by ID |
| PUT | `/api/v1/tasks/{id}` | Update a task |
| DELETE | `/api/v1/tasks/{id}` | Delete a task |
| GET | `/api/v1/tasks/statistics` | Get task statistics |

#### Create a Task

```
POST /api/v1/tasks
Authorization: Bearer <token>
Content-Type: application/json

{
  "title": "Complete project",
  "description": "Finish the SmartTaskManager project",
  "status": "TODO",
  "priority": "HIGH",
  "dueDate": "2027-12-31T23:59:59Z",
  "categoryId": 1
}
```

**Response (201 CREATED):**
```json
{
  "success": true,
  "message": "Task created successfully.",
  "data": {
    "id": 1,
    "title": "Complete project",
    "description": "Finish the SmartTaskManager project",
    "status": "TODO",
    "priority": "HIGH",
    "dueDate": "2027-12-31T23:59:59.000Z",
    "completedAt": null,
    "createdAt": "2026-10-29T18:00:00.000Z",
    "updatedAt": "2026-10-29T18:00:00.000Z",
    "category": {
      "id": 1,
      "name": "Work"
    }
  },
  "timestamp": "2026-10-29T18:00:00.000Z"
}
```

**Valid values:**
- `status`: `TODO`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`
- `priority`: `LOW`, `MEDIUM`, `HIGH`, `URGENT`
- `categoryId`: optional, must belong to the authenticated user

#### List Tasks with Filtering and Pagination

```
GET /api/v1/tasks?status=TODO&priority=HIGH&search=project&page=0&size=20&sortBy=createdAt&sortDirection=DESC
Authorization: Bearer <token>
```

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `status` | String | — | Filter by status (`TODO`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`) |
| `priority` | String | — | Filter by priority (`LOW`, `MEDIUM`, `HIGH`, `URGENT`) |
| `categoryId` | Long | — | Filter by category ID |
| `search` | String | — | Search in task titles and descriptions (case-insensitive) |
| `page` | int | `0` | Page number (0-based) |
| `size` | int | `20` | Page size (max 100) |
| `sortBy` | String | `createdAt` | Sort field (`createdAt`, `updatedAt`, `title`, `status`, `priority`, `dueDate`) |
| `sortDirection` | String | `DESC` | Sort direction (`ASC` or `DESC`) |

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Tasks retrieved successfully.",
  "data": {
    "content": [
      {
        "id": 1,
        "title": "Complete project",
        "status": "TODO",
        "priority": "HIGH",
        "dueDate": "2027-12-31T23:59:59.000Z",
        "completedAt": null,
        "category": { "id": 1, "name": "Work" }
      }
    ],
    "number": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "first": true,
    "last": true
  },
  "timestamp": "2026-10-29T18:00:00.000Z"
}
```

#### Get Task by ID

```
GET /api/v1/tasks/1
Authorization: Bearer <token>
```

**Response (200 OK):** Returns the task data. Returns `404` if the task doesn't exist or belongs to another user.

#### Update a Task

```
PUT /api/v1/tasks/1
Authorization: Bearer <token>
Content-Type: application/json

{
  "title": "Updated title",
  "status": "COMPLETED"
}
```

**Response (200 OK):** Returns the updated task. All fields are optional; only provided fields are updated. When `status` changes to `COMPLETED`, the `completedAt` timestamp is automatically set. When changed from `COMPLETED` to another status, `completedAt` is cleared.

#### Delete a Task

```
DELETE /api/v1/tasks/1
Authorization: Bearer <token>
```

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Task deleted successfully.",
  "timestamp": "2026-10-29T18:00:00.000Z"
}
```

#### Get Task Statistics

```
GET /api/v1/tasks/statistics
Authorization: Bearer <token>
```

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Task statistics retrieved successfully.",
  "data": {
    "totalTasks": 10,
    "completedTasks": 3,
    "pendingTasks": 7,
    "overdueTasks": 2,
    "byStatus": {
      "TODO": 4,
      "IN_PROGRESS": 3,
      "COMPLETED": 3,
      "CANCELLED": 0
    },
    "byPriority": {
      "LOW": 2,
      "MEDIUM": 4,
      "HIGH": 3,
      "URGENT": 1
    }
  },
  "timestamp": "2026-10-29T18:00:00.000Z"
}
```

**Overdue definition:** Tasks with a due date in the past whose status is not `COMPLETED` or `CANCELLED`.

### Reminder Management Endpoints

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/v1/reminders` | Create a reminder |
| GET | `/api/v1/reminders` | List the authenticated user's reminders |
| GET | `/api/v1/reminders/{id}` | Get a reminder by ID |
| PUT | `/api/v1/reminders/{id}` | Update a reminder |
| DELETE | `/api/v1/reminders/{id}` | Delete a reminder |

All reminder endpoints require authentication. Users can only access their own reminders and can only create reminders for their own tasks.

#### Create a Reminder

```
POST /api/v1/reminders
Authorization: Bearer <token>
Content-Type: application/json

{
  "taskId": 1,
  "scheduledAt": "2027-06-01T09:00:00Z"
}
```

**Response (201 CREATED):**
```json
{
  "success": true,
  "message": "Reminder created successfully.",
  "data": {
    "id": 1,
    "taskId": 1,
    "taskTitle": "Complete project",
    "scheduledAt": "2027-06-01T09:00:00Z",
    "status": "PENDING",
    "sentAt": null,
    "retryCount": 0,
    "createdAt": "2026-10-29T18:00:00.000Z"
  },
  "timestamp": "2026-10-29T18:00:00.000Z"
}
```

**Validation rules:**
- `taskId` is required and must reference a task owned by the authenticated user.
- `scheduledAt` is required and must be in the future.
- Returns `404` if the task does not exist or does not belong to the user.
- Returns `400` if the scheduled date is in the past.

#### List Reminders

```
GET /api/v1/reminders
Authorization: Bearer <token>
```

**Response (200 OK):** Returns an array of all reminders belonging to the authenticated user.

#### Get Reminder by ID

```
GET /api/v1/reminders/1
Authorization: Bearer <token>
```

**Response (200 OK):** Returns the reminder if it belongs to the authenticated user. Returns `403` if the reminder belongs to another user.

#### Update a Reminder

```
PUT /api/v1/reminders/1
Authorization: Bearer <token>
Content-Type: application/json

{
  "scheduledAt": "2027-06-02T10:00:00Z"
}
```

**Response (200 OK):** Returns the updated reminder.

**Business rules:**
- Only `PENDING` or `FAILED` reminders can be updated. `SENT` and `CANCELLED` reminders are immutable.
- Returns `403` if the reminder does not belong to the authenticated user.

#### Delete a Reminder

```
DELETE /api/v1/reminders/1
Authorization: Bearer <token>
```

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Reminder deleted successfully.",
  "timestamp": "2026-10-29T18:00:00.000Z"
}
```

### Reminder Scheduler

The application includes a background scheduler that processes due reminders automatically:

- **Interval**: Configurable via `app.reminder.scheduler-interval-ms` (default: 300000ms / 5 minutes).
- **Batch size**: Configurable via `app.reminder.max-batch-size` (default: 50 reminders per run).
- **Max retries**: Configurable via `app.reminder.max-retries` (default: 3).

**How it works:**
1. The scheduler queries for `PENDING` reminders whose `scheduledAt` time has passed.
2. For each due reminder, it sends a reminder email to the task owner.
3. On success, the reminder status changes to `SENT` and `sentAt` is recorded.
4. On failure, the `retryCount` is incremented and the failure reason is recorded.
5. After 3 failed attempts, the reminder status changes to `FAILED`.
6. Each reminder is processed independently — a failure in one does not block others.

**Duplicate prevention:**
- Only `PENDING` reminders are processed. Once a reminder is marked `SENT`, it will never be reprocessed.
- The status-based state transition (PENDING → SENT) ensures at-most-once email delivery in a single-instance deployment.

**Known limitations:**
- In a multi-instance deployment, two instances could theoretically pick up the same `PENDING` reminder before either updates its status. For production multi-instance deployments, consider adding a database-level optimistic lock or distributed lock.
- SMTP timeouts can cause ambiguous delivery: the server may accept the email even if the application never receives confirmation. In such cases, the reminder is retried on the next scheduler run.

### Testing with cURL

```bash
# Login to get a token
curl -X POST http://localhost:9999/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"YourPassword@123"}'

# Create a category (replace <TOKEN> with the accessToken from login)
curl -X POST http://localhost:9999/api/v1/categories \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"name":"Health","description":"Fitness tasks","color":"#00FF00"}'

# List categories
curl -X GET http://localhost:9999/api/v1/categories \
  -H "Authorization: Bearer <TOKEN>"

# Create a task
curl -X POST http://localhost:9999/api/v1/tasks \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"title":"Run 5km","priority":"HIGH","status":"TODO"}'

# List tasks with filters
curl -X GET "http://localhost:9999/api/v1/tasks?status=TODO&priority=HIGH&page=0&size=10" \
  -H "Authorization: Bearer <TOKEN>"

# Get task statistics
curl -X GET http://localhost:9999/api/v1/tasks/statistics \
  -H "Authorization: Bearer <TOKEN>"

# Create a reminder
curl -X POST http://localhost:9999/api/v1/reminders \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"taskId":1,"scheduledAt":"2027-06-01T09:00:00Z"}'

# List reminders
curl -X GET http://localhost:9999/api/v1/reminders \
  -H "Authorization: Bearer <TOKEN>"
```

### Testing with Postman

1. **Login**: Send a `POST` to `http://localhost:9999/api/v1/auth/login` with JSON body `{"email":"user@example.com","password":"YourPassword@123"}`.
2. **Copy the token**: From the response, copy the `data.accessToken` value.
3. **Set the header**: For all subsequent requests, add a header `Authorization: Bearer <your_token>`.
4. **Create a category**: `POST` to `http://localhost:9999/api/v1/categories` with JSON body `{"name":"Work","description":"My work tasks"}`.
5. **List categories**: `GET` to `http://localhost:9999/api/v1/categories`.
6. **Update a category**: `PUT` to `http://localhost:9999/api/v1/categories/{id}` with JSON body `{"name":"Updated Name"}`.
7. **Delete a category**: `DELETE` to `http://localhost:9999/api/v1/categories/{id}`.
8. **Create a task**: `POST` to `http://localhost:9999/api/v1/tasks` with JSON body `{"title":"My task","priority":"HIGH"}`.
9. **List tasks**: `GET` to `http://localhost:9999/api/v1/tasks?status=TODO&page=0&size=10`.
10. **Update a task**: `PUT` to `http://localhost:9999/api/v1/tasks/{id}` with JSON body `{"status":"COMPLETED"}`.
11. **Get statistics**: `GET` to `http://localhost:9999/api/v1/tasks/statistics`.
12. **Delete a task**: `DELETE` to `http://localhost:9999/api/v1/tasks/{id}`.
13. **Create a reminder**: `POST` to `http://localhost:9999/api/v1/reminders` with JSON body `{"taskId":1,"scheduledAt":"2027-06-01T09:00:00Z"}`.
14. **List reminders**: `GET` to `http://localhost:9999/api/v1/reminders`.
15. **Delete a reminder**: `DELETE` to `http://localhost:9999/api/v1/reminders/{id}`.

---

## Security Considerations

- **JWT Authentication**: All protected endpoints require a valid JWT Bearer token.
- **Ownership Enforcement**: Every category and task operation is scoped to the authenticated user. Users cannot access, modify, or delete another user's resources.
- **Category Ownership**: Category names are unique per user (case-insensitive). Tasks can only reference categories owned by the authenticated user.
- **Task Ownership**: Users can only create, read, update, and delete their own tasks. Category assignments are validated against user ownership.
- **Reminder Ownership**: Users can only create, read, update, and delete their own reminders. Reminders can only be created for tasks owned by the authenticated user.
- **Safe Deletion**: Deleting a category sets associated tasks' category to `NULL` instead of deleting the tasks.
- **Reminder Duplicate Prevention**: The scheduler uses status-based state transitions (PENDING → SENT) to prevent duplicate email delivery. Only PENDING reminders are processed.
- **Reminder Retry Policy**: Failed email delivery is retried up to 3 times. After 3 failures, the reminder status changes to FAILED.
- **Password Security**: Passwords are hashed with BCrypt.
- **Token Security**: Refresh tokens are stored as SHA-256 hashes in the database; raw tokens are sent only once to the client.
- **Rate Limiting**: Login and password reset endpoints are rate-limited to prevent abuse.
- **Input Validation**: All request DTOs use Jakarta Bean Validation annotations. Sort fields are allowlisted to prevent injection.
- **Pagination Safety**: Maximum page size is enforced (100) to prevent excessive data retrieval.
- **CORS Protection**: Cross-Origin Resource Sharing is configured to allow only trusted origins (configurable via `APP_CORS_ORIGINS` environment variable).
- **Request Size Limits**: Request body size is limited to 1MB to prevent large payload attacks.
- **Security Headers**: Response headers include `X-Content-Type-Options: nosniff`, `X-Frame-Options: SAMEORIGIN`, and `Cache-Control: no-cache` to prevent clickjacking and MIME-type sniffing attacks.

---

## Production Deployment

### Required Environment Variables

For production deployment, set these environment variables:

| Variable | Description | Required |
| :--- | :--- | :--- |
| `DB_URL` | MySQL JDBC connection URL | ✅ Yes |
| `DB_USERNAME` | MySQL database username | ✅ Yes |
| `DB_PASSWORD` | MySQL database password | ✅ Yes |
| `JWT_SECRET` | HMAC-SHA256 signing key (use `openssl rand -hex 64`) | ✅ Yes |
| `SMTP_HOST` | SMTP server hostname | ✅ Yes |
| `SMTP_PORT` | SMTP server port | ✅ Yes |
| `SMTP_USERNAME` | SMTP authentication username | ✅ Yes |
| `SMTP_PASSWORD` | SMTP authentication password | ✅ Yes |
| `MAIL_FROM` | Sender email address | ✅ Yes |
| `MAIL_FROM_NAME` | Sender display name | ✅ Yes |
| `APP_PUBLIC_BASE_URL` | Public base URL for email links | ✅ Yes |
| `APP_CORS_ORIGINS` | Comma-separated list of allowed frontend origins | ⚠️ Recommended |

### Optional Configuration

| Variable | Description | Default |
| :--- | :--- | :--- |
| `JWT_ACCESS_EXPIRATION_MS` | JWT access token lifetime | 900000 (15 min) |
| `JWT_REFRESH_EXPIRATION_MS` | Refresh token lifetime | 604800000 (7 days) |
| `JWT_VERIFICATION_EXPIRATION_MS` | Email verification token lifetime | 86400000 (24 hours) |
| `JWT_RESET_EXPIRATION_MS` | Password reset token lifetime | 3600000 (1 hour) |
| `REMINDER_SCHEDULER_INTERVAL_MS` | Reminder scheduler polling interval | 300000 (5 min) |
| `REMINDER_MAX_BATCH_SIZE` | Maximum reminders processed per run | 50 |
| `REMINDER_MAX_RETRIES` | Maximum email delivery retry attempts | 3 |

### Deployment Checklist

1. **Generate a strong JWT secret**: Use `openssl rand -hex 64` to generate a 256-bit key.
2. **Configure database**: Create the MySQL database and set up environment variables.
3. **Configure SMTP**: Set up email server credentials for verification and reminder emails.
4. **Set CORS origins**: Configure `APP_CORS_ORIGINS` to match your React frontend's URL.
5. **Disable debug features**: Ensure `SHOW_SQL=false` and `HIBERNATE_DDL_AUTO=validate` in production.
6. **Use HTTPS**: Deploy behind a reverse proxy (nginx, Apache) with TLS termination.
7. **Monitor health checks**: Use `/actuator/health` for liveness and readiness probes.
8. **Backup database**: Implement regular MySQL backups.

### Known Limitations

- **In-memory rate limiting**: Rate limiting state is kept in JVM memory and does not survive application restarts or synchronize across multiple instances. For distributed rate limiting, consider using Redis.
- **Multi-instance reminder processing**: In a multi-instance deployment, two instances could theoretically pick up the same `PENDING` reminder before either updates its status. For production multi-instance deployments, consider adding a database-level optimistic lock or distributed lock.
- **SMTP delivery ambiguity**: SMTP timeouts can cause ambiguous delivery — the server may accept the email even if the application never receives confirmation. The reminder will be retried on the next scheduler run.
- **Single database**: The application uses a single MySQL database instance. For high availability, consider MySQL replication or clustering.
