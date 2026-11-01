package com.smarttaskmanager.taskcapture.service;

import com.smarttaskmanager.category.entity.Category;
import com.smarttaskmanager.category.repository.CategoryRepository;
import com.smarttaskmanager.common.exception.BusinessRuleException;
import com.smarttaskmanager.common.exception.ResourceNotFoundException;
import com.smarttaskmanager.reminder.service.ReminderService;
import com.smarttaskmanager.scheduling.entity.UserSchedulingPreferences;
import com.smarttaskmanager.scheduling.repository.UserSchedulingPreferencesRepository;
import com.smarttaskmanager.task.dto.CreateTaskRequest;
import com.smarttaskmanager.task.dto.TaskResponse;
import com.smarttaskmanager.task.entity.TaskPriority;
import com.smarttaskmanager.task.entity.TaskStatus;
import com.smarttaskmanager.task.service.TaskRecurrenceService;
import com.smarttaskmanager.task.service.TaskService;
import com.smarttaskmanager.taskcapture.dto.ParsedTaskResult;
import com.smarttaskmanager.taskcapture.dto.TaskCaptureConfirmRequest;
import com.smarttaskmanager.taskcapture.dto.TaskCaptureParseResponse;
import com.smarttaskmanager.taskcapture.provider.TaskCaptureProvider;
import com.smarttaskmanager.user.entity.User;
import com.smarttaskmanager.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for AI-powered task capture.
 * Parses natural language into structured task data and creates tasks on user confirmation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskCaptureService {

    private final TaskCaptureProvider taskCaptureProvider;
    private final TaskService taskService;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final UserSchedulingPreferencesRepository preferencesRepository;
    private final TaskRecurrenceService recurrenceService;

    /**
     * Parse natural language input into a structured task preview.
     * Does NOT create a task; the user must confirm first.
     *
     * @param text the natural language input
     * @return parsed task data as a preview
     */
    @Transactional(readOnly = true)
    public TaskCaptureParseResponse parse(String text) {
        User user = getCurrentUser();
        String timezone = preferencesRepository.findByUserId(user.getId())
                .map(UserSchedulingPreferences::getTimezone)
                .orElse("UTC");

        log.info("Parsing task input for user [{}]: {}", user.getEmail(), text.length() > 100 ? text.substring(0, 100) + "..." : text);

        ParsedTaskResult result = taskCaptureProvider.parse(text, timezone);

        return TaskCaptureParseResponse.fromResult(result, taskCaptureProvider.getType());
    }

    /**
     * Confirm and create a task from parsed data.
     * Creates the task, optionally creates a category, and optionally creates a reminder.
     *
     * @param request the confirmed task data
     * @return the created task response
     */
    @Transactional
    public TaskResponse confirm(TaskCaptureConfirmRequest request) {
        User user = getCurrentUser();

        // Validate title
        String title = request.title().trim();
        if (title.isEmpty()) {
            throw new BusinessRuleException("Task title cannot be empty.");
        }
        if (title.length() > 150) {
            throw new BusinessRuleException("Task title must not exceed 150 characters.");
        }

        // Find or create category if specified
        Long categoryId = null;
        if (request.categoryName() != null && !request.categoryName().isBlank()) {
            Category category = categoryRepository.findByNameIgnoreCaseAndUserId(
                    request.categoryName().trim(), user.getId()).orElse(null);
            if (category == null) {
                // Create the category if it doesn't exist
                category = Category.builder()
                        .user(user)
                        .name(request.categoryName().trim())
                        .build();
                category = categoryRepository.save(category);
                log.info("Created category [{}] for user [{}]", category.getName(), user.getEmail());
            }
            categoryId = category.getId();
        }

        // Build the task creation request
        CreateTaskRequest createRequest = new CreateTaskRequest(
                title,
                request.description(),
                request.status() != null ? request.status() : TaskStatus.TODO,
                request.priority() != null ? request.priority() : TaskPriority.MEDIUM,
                request.dueDate(),
                categoryId
        );

        // Create the task using the existing TaskService
        TaskResponse taskResponse = taskService.createTask(createRequest);

        log.info("Task captured: [{}] for user [{}]", taskResponse.title(), user.getEmail());

        // Create recurrence if specified
        if (request.recurrenceType() != null && taskResponse.id() != null) {
            try {
                recurrenceService.createRecurrence(
                        taskResponse.id(),
                        new com.smarttaskmanager.task.dto.CreateRecurrenceRequest(
                                request.recurrenceType(),
                                request.recurrenceInterval() != null ? request.recurrenceInterval() : 1,
                                null, null, null, null, null
                        )
                );
                log.info("Recurrence rule created for task [{}]", taskResponse.id());
            } catch (Exception e) {
                log.warn("Failed to create recurrence for task [{}]: {}", taskResponse.id(), e.getMessage());
            }
        }

        return taskResponse;
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }
}
