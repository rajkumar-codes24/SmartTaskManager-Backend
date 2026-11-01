package com.smarttaskmanager.task.service;

import com.smarttaskmanager.common.exception.DuplicateResourceException;
import com.smarttaskmanager.common.exception.ResourceNotFoundException;
import com.smarttaskmanager.task.dto.CreateLabelRequest;
import com.smarttaskmanager.task.dto.LabelResponse;
import com.smarttaskmanager.task.dto.UpdateLabelRequest;
import com.smarttaskmanager.task.entity.Label;
import com.smarttaskmanager.task.entity.Task;
import com.smarttaskmanager.task.repository.LabelRepository;
import com.smarttaskmanager.task.repository.TaskRepository;
import com.smarttaskmanager.user.entity.User;
import com.smarttaskmanager.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for managing labels scoped to the authenticated user.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LabelService {

    private final LabelRepository labelRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;

    @Transactional
    public LabelResponse createLabel(CreateLabelRequest request) {
        User user = getCurrentUser();
        String normalizedName = request.name().trim();

        if (labelRepository.existsByUserIdAndNameIgnoreCase(user.getId(), normalizedName)) {
            throw new DuplicateResourceException("Label", "name", normalizedName);
        }

        Label label = Label.builder()
                .user(user)
                .name(normalizedName)
                .color(request.color())
                .build();

        label = labelRepository.save(label);
        log.info("Label [{}] created for user [{}]", label.getName(), user.getEmail());
        return toResponse(label);
    }

    @Transactional(readOnly = true)
    public List<LabelResponse> getUserLabels() {
        User user = getCurrentUser();
        return labelRepository.findByUserId(user.getId()).stream()
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public LabelResponse getLabelById(Long labelId) {
        User user = getCurrentUser();
        Label label = labelRepository.findByIdAndUserId(labelId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Label", "id", labelId));
        return toResponse(label);
    }

    @Transactional
    public LabelResponse updateLabel(Long labelId, UpdateLabelRequest request) {
        User user = getCurrentUser();
        Label label = labelRepository.findByIdAndUserId(labelId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Label", "id", labelId));

        if (request.name() != null) {
            String normalizedName = request.name().trim();
            if (labelRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(user.getId(), normalizedName, labelId)) {
                throw new DuplicateResourceException("Label", "name", normalizedName);
            }
            label.setName(normalizedName);
        }
        if (request.color() != null) {
            label.setColor(request.color());
        }

        label = labelRepository.save(label);
        log.info("Label [{}] updated for user [{}]", label.getName(), user.getEmail());
        return toResponse(label);
    }

    @Transactional
    public void deleteLabel(Long labelId) {
        User user = getCurrentUser();
        Label label = labelRepository.findByIdAndUserId(labelId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Label", "id", labelId));
        labelRepository.delete(label);
        log.info("Label [{}] deleted for user [{}]", label.getName(), user.getEmail());
    }

    @Transactional
    public void assignLabelToTask(Long taskId, Long labelId) {
        User user = getCurrentUser();
        Task task = taskRepository.findByIdAndUserId(taskId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Task", "id", taskId));
        Label label = labelRepository.findByIdAndUserId(labelId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Label", "id", labelId));

        entityManager.createNativeQuery(
                "INSERT INTO task_labels (task_id, label_id) VALUES (:taskId, :labelId)")
                .setParameter("taskId", task.getId())
                .setParameter("labelId", label.getId())
                .executeUpdate();
        log.info("Label [{}] assigned to task [{}] for user [{}]", label.getName(), taskId, user.getEmail());
    }

    @Transactional
    public void removeLabelFromTask(Long taskId, Long labelId) {
        User user = getCurrentUser();
        taskRepository.findByIdAndUserId(taskId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Task", "id", taskId));
        labelRepository.findByIdAndUserId(labelId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Label", "id", labelId));

        entityManager.createNativeQuery(
                "DELETE FROM task_labels WHERE task_id = :taskId AND label_id = :labelId")
                .setParameter("taskId", taskId)
                .setParameter("labelId", labelId)
                .executeUpdate();
        log.info("Label [{}] removed from task [{}] for user [{}]", labelId, taskId, user.getEmail());
    }

    @Transactional(readOnly = true)
    public List<LabelResponse> getLabelsByTaskId(Long taskId) {
        User user = getCurrentUser();
        taskRepository.findByIdAndUserId(taskId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Task", "id", taskId));

        List<Label> labels = entityManager.createNativeQuery(
                "SELECT l.* FROM labels l INNER JOIN task_labels tl ON l.id = tl.label_id WHERE tl.task_id = :taskId", Label.class)
                .setParameter("taskId", taskId)
                .getResultList();
        return labels.stream().map(this::toResponse).toList();
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    private LabelResponse toResponse(Label label) {
        return new LabelResponse(label.getId(), label.getName(), label.getColor());
    }
}
