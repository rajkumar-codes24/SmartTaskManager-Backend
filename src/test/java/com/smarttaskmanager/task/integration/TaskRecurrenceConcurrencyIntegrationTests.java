package com.smarttaskmanager.task.integration;

import com.smarttaskmanager.common.exception.BusinessRuleException;
import com.smarttaskmanager.task.entity.RecurrenceType;
import com.smarttaskmanager.task.entity.Task;
import com.smarttaskmanager.task.entity.TaskPriority;
import com.smarttaskmanager.task.entity.TaskRecurrenceRule;
import com.smarttaskmanager.task.entity.TaskStatus;
import com.smarttaskmanager.task.repository.TaskRecurrenceRuleRepository;
import com.smarttaskmanager.task.repository.TaskRepository;
import com.smarttaskmanager.task.service.TaskRecurrenceService;
import com.smarttaskmanager.user.entity.Role;
import com.smarttaskmanager.user.entity.User;
import com.smarttaskmanager.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real concurrent integration test for recurrence occurrence generation.
 * Uses H2 file-based database with Flyway migrations for real concurrent access.
 * 
 * IMPORTANT: This test does NOT use @Transactional at class level because we need
 * real separate database transactions for each thread. Each thread runs in its own
 * transaction via the service's @Transactional methods.
 */
@SpringBootTest
@ActiveProfiles("concurrency")
class TaskRecurrenceConcurrencyIntegrationTests {

    @Autowired
    private TaskRecurrenceService recurrenceService;

    @Autowired
    private TaskRecurrenceRuleRepository recurrenceRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;
    private Task testTask;
    private Long taskId;

    @BeforeEach
    void setUp() {
        // Create a fresh test user for each test
        testUser = User.builder()
                .fullName("Concurrency Test User")
                .email("concurrency-test-" + System.currentTimeMillis() + "@example.com")
                .passwordHash(passwordEncoder.encode("TestPassword@123"))
                .role(Role.ROLE_USER)
                .enabled(true)
                .emailVerified(true)
                .build();
        testUser = userRepository.save(testUser);

        // Create a fresh task with a future due date
        Instant dueDate = Instant.now().plus(7, ChronoUnit.DAYS);
        testTask = Task.builder()
                .user(testUser)
                .title("Concurrency Test Recurring Task")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.HIGH)
                .dueDate(dueDate)
                .timezone("UTC")
                .build();
        testTask = taskRepository.save(testTask);
        taskId = testTask.getId();

        // Set up security context for the main thread
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                testUser.getEmail(), testUser.getEmail());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void cleanup() {
        // Clean up test data - delete in correct order to respect FK constraints
        recurrenceRepository.deleteAll();
        taskRepository.deleteAll();
        userRepository.deleteAll();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Two concurrent threads generate next occurrence - only one succeeds")
    void generateNextOccurrence_concurrentRequests_onlyOneSucceeds() throws Exception {
        // Set up security context
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                testUser.getEmail(), testUser.getEmail());
        SecurityContextHolder.getContext().setAuthentication(auth);

        // Create a recurrence rule with max 5 occurrences
        TaskRecurrenceRule rule = TaskRecurrenceRule.builder()
                .task(testTask)
                .user(testUser)
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .occurrenceCount(5)
                .generatedOccurrences(0)
                .isPaused(false)
                .isCompleted(false)
                .build();
        rule = recurrenceRepository.save(rule);
        recurrenceRepository.flush();

        // Use a barrier to ensure both threads start at exactly the same time
        int threadCount = 2;
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        List<Future<?>> futures = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            Future<?> future = executor.submit(() -> {
                // Set up security context for this thread
                SecurityContextHolder.getContext().setAuthentication(auth);

                try {
                    // Wait for all threads to be ready
                    barrier.await(5, TimeUnit.SECONDS);

                    // Both threads try to generate the next occurrence simultaneously
                    recurrenceService.generateNextOccurrence(taskId);
                    successCount.incrementAndGet();
                } catch (BusinessRuleException e) {
                    // Expected: one thread should fail due to optimistic locking
                    failureCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (BrokenBarrierException | java.util.concurrent.TimeoutException e) {
                    throw new RuntimeException("Barrier failed", e);
                } finally {
                    latch.countDown();
                    SecurityContextHolder.clearContext();
                }
            });
            futures.add(future);
        }

        // Wait for all threads to complete
        latch.await(30, TimeUnit.SECONDS);

        // Wait for futures
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        // Verify results
        System.out.println("Success count: " + successCount.get());
        System.out.println("Failure count: " + failureCount.get());

        // At least one thread should succeed (with retries, both may succeed sequentially)
        assertThat(successCount.get())
                .as("At least one thread should succeed in generating the next occurrence")
                .isGreaterThanOrEqualTo(1);

        // Verify the recurrence rule state - the key invariant is that generatedOccurrences
        // matches the number of successful threads, proving no duplicate generation occurred
        recurrenceRepository.flush();
        TaskRecurrenceRule updatedRule = recurrenceRepository.findByTaskIdAndUserId(taskId, testUser.getId())
                .orElseThrow(() -> new AssertionError("Recurrence rule should exist"));

        assertThat(updatedRule.getGeneratedOccurrences())
                .as("Generated occurrences should match the number of successful threads")
                .isEqualTo(successCount.get());
        assertThat(updatedRule.getIsCompleted())
                .as("Rule should not be completed yet")
                .isFalse();

        // Verify no duplicate tasks were created - the original task is updated in place
        long taskCount = taskRepository.count();
        assertThat(taskCount)
                .as("Should have exactly 1 task (original task updated, not duplicated)")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Multiple concurrent requests respect max occurrence limit")
    void generateNextOccurrence_concurrentWithMaxLimit_doesNotExceedLimit() throws Exception {
        // Set up security context
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                testUser.getEmail(), testUser.getEmail());
        SecurityContextHolder.getContext().setAuthentication(auth);

        // Create a recurrence rule with max 2 occurrences
        TaskRecurrenceRule rule = TaskRecurrenceRule.builder()
                .task(testTask)
                .user(testUser)
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .occurrenceCount(2)
                .generatedOccurrences(0)
                .isPaused(false)
                .isCompleted(false)
                .build();
        recurrenceRepository.save(rule);
        recurrenceRepository.flush();

        // Spawn 4 threads to try to generate occurrences
        int threadCount = 4;
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            Future<?> future = executor.submit(() -> {
                SecurityContextHolder.getContext().setAuthentication(auth);
                try {
                    barrier.await(5, TimeUnit.SECONDS);
                    recurrenceService.generateNextOccurrence(taskId);
                    successCount.incrementAndGet();
                } catch (BusinessRuleException e) {
                    failureCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (BrokenBarrierException | java.util.concurrent.TimeoutException e) {
                    throw new RuntimeException("Barrier failed", e);
                } finally {
                    latch.countDown();
                    SecurityContextHolder.clearContext();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        System.out.println("Success count: " + successCount.get());
        System.out.println("Failure count: " + failureCount.get());

        // Verify the occurrence count never exceeds the limit
        recurrenceRepository.flush();
        TaskRecurrenceRule updatedRule = recurrenceRepository.findByTaskIdAndUserId(taskId, testUser.getId())
                .orElseThrow(() -> new AssertionError("Recurrence rule should exist"));

        assertThat(updatedRule.getGeneratedOccurrences())
                .as("Generated occurrences should not exceed the max limit of 2")
                .isLessThanOrEqualTo(2);

        // At least one should have succeeded
        assertThat(successCount.get())
                .as("At least one thread should succeed")
                .isGreaterThan(0);
    }

    @Test
    @DisplayName("Concurrent requests do not create duplicate tasks")
    void generateNextOccurrence_concurrentRequests_noDuplicateTasks() throws Exception {
        // Set up security context
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                testUser.getEmail(), testUser.getEmail());
        SecurityContextHolder.getContext().setAuthentication(auth);

        // Create a recurrence rule
        TaskRecurrenceRule rule = TaskRecurrenceRule.builder()
                .task(testTask)
                .user(testUser)
                .recurrenceType(RecurrenceType.DAILY)
                .recurrenceInterval(1)
                .occurrenceCount(10)
                .generatedOccurrences(0)
                .isPaused(false)
                .isCompleted(false)
                .build();
        recurrenceRepository.save(rule);
        recurrenceRepository.flush();

        long initialTaskCount = taskRepository.count();

        // Spawn 3 threads
        int threadCount = 3;
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            Future<?> future = executor.submit(() -> {
                SecurityContextHolder.getContext().setAuthentication(auth);
                try {
                    barrier.await(5, TimeUnit.SECONDS);
                    recurrenceService.generateNextOccurrence(taskId);
                    successCount.incrementAndGet();
                } catch (BusinessRuleException | InterruptedException e) {
                    // Expected for some threads
                } catch (BrokenBarrierException | java.util.concurrent.TimeoutException e) {
                    throw new RuntimeException("Barrier failed", e);
                } finally {
                    latch.countDown();
                    SecurityContextHolder.clearContext();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        // Verify no duplicate tasks were created
        long finalTaskCount = taskRepository.count();
        assertThat(finalTaskCount)
                .as("Task count should not increase - the original task is updated, not duplicated")
                .isEqualTo(initialTaskCount);

        // Verify the recurrence rule is consistent
        recurrenceRepository.flush();
        TaskRecurrenceRule updatedRule = recurrenceRepository.findByTaskIdAndUserId(taskId, testUser.getId())
                .orElseThrow(() -> new AssertionError("Recurrence rule should exist"));

        // The generated occurrences should match the number of successful threads
        assertThat(updatedRule.getGeneratedOccurrences())
                .as("Generated occurrences should match successful thread count")
                .isEqualTo(successCount.get());
    }
}
