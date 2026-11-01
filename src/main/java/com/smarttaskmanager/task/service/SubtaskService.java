package com.smarttaskmanager.task.service;

import com.smarttaskmanager.common.exception.ResourceNotFoundException;
import com.smarttaskmanager.task.dto.CreateSubtaskRequest;
import com.smarttaskmanager.task.dto.SubtaskResponse;
import com.smarttaskmanager.task.dto.UpdateSubtaskRequest;
import com.smarttaskmanager.task.entity.Subtask;
import com.smarttaskmanager.task.entity.Task;
import com.smarttaskmanager.task.entity.TaskPriority;
import com.smarttaskmanager.task.entity.TaskStatus;
import com.smarttaskmanager.task.repository.SubtaskRepository;
import com.smarttaskmanager.task.repository.TaskRepository;
import com.smarttaskmanager.user.entity.User;
import com.smarttaskmanager.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Service for managing subtasks scoped to the authenticated user.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubtaskService {

    private final SubtaskRepository subtaskRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    @Transactional
    public SubtaskResponse createSubtask(Long taskId, CreateSubtaskRequest request) {
        User user = getCurrentUser();
        Task parentTask = taskRepository.findByIdAndUserId(taskId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Task", "id", taskId));

        int nextPosition = (int) subtaskRepository.countByParentTaskIdAndUserId(taskId, user.getId());

        Subtask subtask = Subtask.builder()
                .parentTask(parentTask)
                .user(user)
                .title(request.title().trim())
                .description(request.description())
                .status(request.status() != null ? request.status() : TaskStatus.TODO)
                .priority(request.priority() != null ? request.priority() : TaskPriority.MEDIUM)
                .dueDate(request.dueDate())
                .position(nextPosition)
                .build();

        subtask = subtaskRepository.save(subtask);
        log.info("Subtask [{}] created for task [{}] by user [{}]", subtask.getTitle(), taskId, user.getEmail());
        return toResponse(subtask);
    }

    @Transactional(readOnly = true)
    public List<SubtaskResponse> getSubtasksByTaskId(Long taskId) {
        User user = getCurrentUser();
        taskRepository.findByIdAndUserId(taskId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Task", "id", taskId));

        List<Subtask> subtasks = subtaskRepository.findByParentTaskIdAndUserId(taskId, user.getId());
        return subtasks.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public SubtaskResponse getSubtaskById(Long taskId, Long subtaskId) {
        User user = getCurrentUser();
        Subtask subtask = subtaskRepository.findByIdAndUserId(subtaskId, user.getId());
        if (subtask == null || !subtask.getParentTask().getId().equals(taskId)) {
            throw new ResourceNotFoundException("Subtask", "id", subtaskId);
        }
        return toResponse(subtask);
    }

    @Transactional
    public SubtaskResponse updateSubtask(Long taskId, Long subtaskId, UpdateSubtaskRequest request) {
        User user = getCurrentUser();
        Subtask subtask = subtaskRepository.findByIdAndUserId(subtaskId, user.getId());
        if (subtask == null || !subtask.getParentTask().getId().equals(taskId)) {
            throw new ResourceNotFoundException("Subtask", "id", subtaskId);
        }

        if (request.title() != null) {
            subtask.setTitle(request.title().trim());
        }
        if (request.description() != null) {
            subtask.setDescription(request.description());
        }
        if (request.status() != null) {
            subtask.setStatus(request.status());
            if (request.status() == TaskStatus.COMPLETED && subtask.getCompletedAt() == null) {
                subtask.setCompletedAt(Instant.now());
            } else if (request.status() != TaskStatus.COMPLETED) {
                subtask.setCompletedAt(null);
            }
        }
        if (request.priority() != null) {
            subtask.setPriority(request.priority());
        }
        if (request.dueDate() != null) {
            subtask.setDueDate(request.dueDate());
        }

        subtask = subtaskRepository.save(subtask);
        log.info("Subtask [{}] updated for user [{}]", subtask.getTitle(), user.getEmail());
        return toResponse(subtask);
    }

    @Transactional
    public void deleteSubtask(Long taskId, Long subtaskId) {
        User user = getCurrentUser();
        Subtask subtask = subtaskRepository.findByIdAndUserId(subtaskId, user.getId());
        if (subtask == null || !subtask.getParentTask().getId().equals(taskId)) {
            throw new ResourceNotFoundException("Subtask", "id", subtaskId);
        }
        subtaskRepository.delete(subtask);
        log.info("Subtask [{}] deleted for user [{}]", subtask.getTitle(), user.getEmail());
    }

    /**
     * Calculates the completion progress of a parent task based on its subtasks.
     *
     * @return completion percentage (0-100), or -1 if no subtasks exist
     */
    @Transactional(readOnly = true)
    public int getCompletionProgress(Long taskId) {
        User user = getCurrentUser();
        long total = subtaskRepository.countByParentTaskIdAndUserId(taskId, user.getId());
        if (total == 0) return -1;
        long completed = subtaskRepository.countCompletedByParentTaskIdAndUserId(taskId, user.getId());
        return (int) ((completed * 100) / total);
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    private SubtaskResponse toResponse(Subtask subtask) {
        return new SubtaskResponse(
                subtask.getId(),
                subtask.getParentTask().getId(),
                subtask.getTitle(),
                subtask.getDescription(),
                subtask.getStatus(),
                subtask.getPriority(),
                subtask.getDueDate(),
                subtask.getCompletedAt(),
                subtask.getPosition(),
                subtask.getCreatedAt(),
                subtask.getUpdatedAt()
        );
    }
}
