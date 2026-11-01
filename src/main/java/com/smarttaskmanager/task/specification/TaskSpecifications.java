package com.smarttaskmanager.task.specification;

import com.smarttaskmanager.task.entity.Task;
import com.smarttaskmanager.task.entity.TaskPriority;
import com.smarttaskmanager.task.entity.TaskStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/**
 * JPA Specifications for dynamic task filtering.
 *
 * <p>Used with {@link org.springframework.data.jpa.repository.JpaSpecificationExecutor}
 * to build type-safe, composable queries for task search, filtering, and sorting.</p>
 */
public final class TaskSpecifications {

    private TaskSpecifications() {
        // Utility class
    }

    public static Specification<Task> belongsToUser(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);
    }

    public static Specification<Task> hasStatus(TaskStatus status) {
        return status == null ? Specification.anyOf() : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Task> hasPriority(TaskPriority priority) {
        return priority == null ? Specification.anyOf() : (root, query, cb) -> cb.equal(root.get("priority"), priority);
    }

    public static Specification<Task> hasCategoryId(Long categoryId) {
        return categoryId == null ? Specification.anyOf() : (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<Task> searchTitleOrDescription(String search) {
        if (!StringUtils.hasText(search)) {
            return Specification.anyOf();
        }
        return (root, query, cb) -> {
            String pattern = "%" + search.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern)
            );
        };
    }
}
