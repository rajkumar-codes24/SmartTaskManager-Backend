package com.smarttaskmanager.task.service;

import com.smarttaskmanager.common.exception.BusinessRuleException;
import com.smarttaskmanager.common.exception.ResourceNotFoundException;
import com.smarttaskmanager.task.dto.DependencyStatusResponse;
import com.smarttaskmanager.task.entity.Task;
import com.smarttaskmanager.task.entity.TaskDependency;
import com.smarttaskmanager.task.entity.TaskStatus;
import com.smarttaskmanager.task.repository.TaskDependencyRepository;
import com.smarttaskmanager.task.repository.TaskRepository;
import com.smarttaskmanager.user.entity.User;
import com.smarttaskmanager.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service for managing task dependencies with cycle detection and topological sorting.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskDependencyService {

    private final TaskDependencyRepository dependencyRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    @Transactional
    public void addDependency(Long taskId, Long dependencyTaskId) {
        User user = getCurrentUser();

        // Validate both tasks belong to the user
        Task task = taskRepository.findByIdAndUserId(taskId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Task", "id", taskId));
        Task dependencyTask = taskRepository.findByIdAndUserId(dependencyTaskId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Task", "id", dependencyTaskId));

        // Check for self-dependency
        if (taskId.equals(dependencyTaskId)) {
            throw new BusinessRuleException("A task cannot depend on itself");
        }

        // Check for duplicate dependency
        if (dependencyRepository.existsByTaskIdAndDependencyTaskIdAndUserId(taskId, dependencyTaskId, user.getId())) {
            throw new BusinessRuleException("Dependency already exists");
        }

        // Build dependency graph and check for cycles
        List<TaskDependency> allDeps = dependencyRepository.findByUserId(user.getId());
        Map<Long, Set<Long>> graph = buildDependencyGraph(allDeps);
        graph.computeIfAbsent(taskId, k -> new HashSet<>()).add(dependencyTaskId);

        if (hasCycle(graph)) {
            throw new BusinessRuleException("Adding this dependency would create a circular dependency");
        }

        TaskDependency dependency = TaskDependency.builder()
                .task(task)
                .dependencyTask(dependencyTask)
                .user(user)
                .build();

        dependencyRepository.save(dependency);
        log.info("Dependency added: task [{}] depends on task [{}] for user [{}]", taskId, dependencyTaskId, user.getEmail());
    }

    @Transactional
    public void removeDependency(Long taskId, Long dependencyTaskId) {
        User user = getCurrentUser();
        taskRepository.findByIdAndUserId(taskId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Task", "id", taskId));
        taskRepository.findByIdAndUserId(dependencyTaskId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Task", "id", dependencyTaskId));

        List<TaskDependency> deps = dependencyRepository.findByTaskIdAndUserId(taskId, user.getId());
        TaskDependency toDelete = deps.stream()
                .filter(d -> d.getDependencyTask().getId().equals(dependencyTaskId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("TaskDependency", "task_id", taskId));

        dependencyRepository.delete(toDelete);
        log.info("Dependency removed: task [{}] no longer depends on task [{}] for user [{}]", taskId, dependencyTaskId, user.getEmail());
    }

    @Transactional(readOnly = true)
    public List<Task> getDependencies(Long taskId) {
        User user = getCurrentUser();
        taskRepository.findByIdAndUserId(taskId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Task", "id", taskId));

        List<TaskDependency> deps = dependencyRepository.findByTaskIdAndUserId(taskId, user.getId());
        return deps.stream().map(TaskDependency::getDependencyTask).toList();
    }

    @Transactional(readOnly = true)
    public List<Task> getDependents(Long taskId) {
        User user = getCurrentUser();
        taskRepository.findByIdAndUserId(taskId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Task", "id", taskId));

        List<TaskDependency> deps = dependencyRepository.findByDependencyTaskIdAndUserId(taskId, user.getId());
        return deps.stream().map(TaskDependency::getTask).toList();
    }

    @Transactional(readOnly = true)
    public DependencyStatusResponse getDependencyStatus(Long taskId) {
        User user = getCurrentUser();
        taskRepository.findByIdAndUserId(taskId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Task", "id", taskId));

        List<TaskDependency> deps = dependencyRepository.findByTaskIdAndUserId(taskId, user.getId());
        List<Long> dependencyTaskIds = new ArrayList<>();
        List<Long> completedDependencyIds = new ArrayList<>();

        for (TaskDependency dep : deps) {
            dependencyTaskIds.add(dep.getDependencyTask().getId());
            if (dep.getDependencyTask().getStatus() == TaskStatus.COMPLETED) {
                completedDependencyIds.add(dep.getDependencyTask().getId());
            }
        }

        boolean allCompleted = dependencyTaskIds.size() == completedDependencyIds.size();
        boolean isBlocked = !dependencyTaskIds.isEmpty() && !allCompleted;

        return new DependencyStatusResponse(taskId, dependencyTaskIds, completedDependencyIds, allCompleted, isBlocked);
    }

    /**
     * Returns a topologically sorted list of task IDs for the authenticated user.
     * Tasks with no dependencies come first.
     */
    @Transactional(readOnly = true)
    public List<Long> getTopologicalOrder() {
        User user = getCurrentUser();
        List<TaskDependency> allDeps = dependencyRepository.findByUserId(user.getId());
        Map<Long, Set<Long>> graph = buildDependencyGraph(allDeps);

        // Collect all task IDs from the graph
        Set<Long> allTaskIds = new HashSet<>(graph.keySet());
        for (Set<Long> deps : graph.values()) {
            allTaskIds.addAll(deps);
        }

        return topologicalSort(allTaskIds, graph);
    }

    private Map<Long, Set<Long>> buildDependencyGraph(List<TaskDependency> deps) {
        Map<Long, Set<Long>> graph = new HashMap<>();
        for (TaskDependency dep : deps) {
            graph.computeIfAbsent(dep.getTask().getId(), k -> new HashSet<>())
                    .add(dep.getDependencyTask().getId());
        }
        return graph;
    }

    private boolean hasCycle(Map<Long, Set<Long>> graph) {
        Set<Long> visited = new HashSet<>();
        Set<Long> recursionStack = new HashSet<>();

        for (Long node : graph.keySet()) {
            if (!visited.contains(node)) {
                if (dfsCycle(node, graph, visited, recursionStack)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean dfsCycle(Long node, Map<Long, Set<Long>> graph, Set<Long> visited, Set<Long> recursionStack) {
        visited.add(node);
        recursionStack.add(node);

        for (Long neighbor : graph.getOrDefault(node, Set.of())) {
            if (!visited.contains(neighbor)) {
                if (dfsCycle(neighbor, graph, visited, recursionStack)) {
                    return true;
                }
            } else if (recursionStack.contains(neighbor)) {
                return true;
            }
        }

        recursionStack.remove(node);
        return false;
    }

    private List<Long> topologicalSort(Set<Long> allTaskIds, Map<Long, Set<Long>> graph) {
        Set<Long> visited = new HashSet<>();
        List<Long> result = new ArrayList<>();

        for (Long taskId : allTaskIds) {
            if (!visited.contains(taskId)) {
                topologicalSortDFS(taskId, graph, visited, result);
            }
        }
        return result;
    }

    private void topologicalSortDFS(Long node, Map<Long, Set<Long>> graph, Set<Long> visited, List<Long> result) {
        visited.add(node);
        for (Long neighbor : graph.getOrDefault(node, Set.of())) {
            if (!visited.contains(neighbor)) {
                topologicalSortDFS(neighbor, graph, visited, result);
            }
        }
        result.add(node);
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }
}
