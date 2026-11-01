package com.smarttaskmanager.repository;

import com.smarttaskmanager.auth.entity.EmailVerificationToken;
import com.smarttaskmanager.auth.entity.PasswordResetToken;
import com.smarttaskmanager.auth.entity.RefreshToken;
import com.smarttaskmanager.auth.repository.EmailVerificationTokenRepository;
import com.smarttaskmanager.auth.repository.PasswordResetTokenRepository;
import com.smarttaskmanager.auth.repository.RefreshTokenRepository;
import com.smarttaskmanager.category.entity.Category;
import com.smarttaskmanager.category.repository.CategoryRepository;
import com.smarttaskmanager.reminder.entity.Reminder;
import com.smarttaskmanager.reminder.entity.ReminderStatus;
import com.smarttaskmanager.reminder.repository.ReminderRepository;
import com.smarttaskmanager.task.entity.Task;
import com.smarttaskmanager.task.entity.TaskPriority;
import com.smarttaskmanager.task.entity.TaskStatus;
import com.smarttaskmanager.task.repository.TaskRepository;
import com.smarttaskmanager.user.entity.Role;
import com.smarttaskmanager.user.entity.User;
import com.smarttaskmanager.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class RepositoryPersistenceTests {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ReminderRepository reminderRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .fullName("Jane Doe")
                .email("jane.doe@example.com")
                .passwordHash("$2a$12$e80yq9gQzV9vB9Zk8wUj2.nQh11n8p4j7Wk5Yq7M0l2b3v4c5d6e7")
                .role(Role.ROLE_USER)
                .enabled(true)
                .emailVerified(true)
                .build();
        testUser = entityManager.persistAndFlush(testUser);
    }

    @Test
    @DisplayName("User persistence and unique email constraint")
    void testUserPersistenceAndUniqueEmail() {
        Optional<User> found = userRepository.findByEmailIgnoreCase("jane.doe@example.com");
        assertThat(found).isPresent();
        assertThat(found.get().getFullName()).isEqualTo("Jane Doe");

        User duplicateUser = User.builder()
                .fullName("Duplicate User")
                .email("jane.doe@example.com")
                .passwordHash("somehash")
                .role(Role.ROLE_USER)
                .build();

        assertThatThrownBy(() -> {
            userRepository.saveAndFlush(duplicateUser);
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Category persistence and uniqueness per user constraint")
    void testCategoryUniquenessPerUser() {
        Category cat1 = Category.builder()
                .user(testUser)
                .name("Work")
                .color("#FF0000")
                .build();
        categoryRepository.saveAndFlush(cat1);

        Category duplicateCat = Category.builder()
                .user(testUser)
                .name("Work")
                .color("#00FF00")
                .build();

        assertThatThrownBy(() -> {
            categoryRepository.saveAndFlush(duplicateCat);
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Task persistence, scoped retrieval, and status/priority aggregation")
    void testTaskPersistenceAndAggregations() {
        Category workCat = categoryRepository.saveAndFlush(
                Category.builder().user(testUser).name("Work").build()
        );

        Task task1 = Task.builder()
                .user(testUser)
                .category(workCat)
                .title("Complete Q3 Report")
                .description("Prepare slides and numbers")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.HIGH)
                .dueDate(Instant.now().plus(2, ChronoUnit.DAYS))
                .build();
        taskRepository.saveAndFlush(task1);

        Task task2 = Task.builder()
                .user(testUser)
                .title("Pay utility bill")
                .status(TaskStatus.COMPLETED)
                .priority(TaskPriority.MEDIUM)
                .completedAt(Instant.now())
                .build();
        taskRepository.saveAndFlush(task2);

        // Verify counts
        assertThat(taskRepository.countByUserId(testUser.getId())).isEqualTo(2);
        assertThat(taskRepository.countByUserIdAndStatus(testUser.getId(), TaskStatus.TODO)).isEqualTo(1);
        assertThat(taskRepository.countByUserIdAndStatus(testUser.getId(), TaskStatus.COMPLETED)).isEqualTo(1);

        // Verify aggregation projections
        List<TaskRepository.StatusCountProjection> statusCounts = taskRepository.countTasksByStatusForUser(testUser.getId());
        assertThat(statusCounts).hasSize(2);

        List<TaskRepository.PriorityCountProjection> priorityCounts = taskRepository.countTasksByPriorityForUser(testUser.getId());
        assertThat(priorityCounts).hasSize(2);
    }

    @Test
    @DisplayName("Task ownership isolation prevents cross-user access")
    void testTaskOwnershipIsolation() {
        User otherUser = entityManager.persistAndFlush(
                User.builder()
                        .fullName("Bob Smith")
                        .email("bob.smith@example.com")
                        .passwordHash("somehash")
                        .role(Role.ROLE_USER)
                        .build()
        );

        Task bobsTask = taskRepository.saveAndFlush(
                Task.builder()
                        .user(otherUser)
                        .title("Bob's secret project")
                        .status(TaskStatus.IN_PROGRESS)
                        .priority(TaskPriority.URGENT)
                        .build()
        );

        // Test user attempts to find Bob's task using their own userId
        Optional<Task> unauthorizedAccess = taskRepository.findByIdAndUserId(bobsTask.getId(), testUser.getId());
        assertThat(unauthorizedAccess).isEmpty();

        // Bob accessing his own task succeeds
        Optional<Task> authorizedAccess = taskRepository.findByIdAndUserId(bobsTask.getId(), otherUser.getId());
        assertThat(authorizedAccess).isPresent();
    }

    @Test
    @DisplayName("Reminder persistence and due reminders query")
    void testReminderPersistenceAndQuery() {
        Task task = taskRepository.saveAndFlush(
                Task.builder()
                        .user(testUser)
                        .title("Upcoming Presentation")
                        .dueDate(Instant.now().plus(1, ChronoUnit.HOURS))
                        .build()
        );

        Instant scheduledTime = Instant.now().minus(5, ChronoUnit.MINUTES);
        Reminder reminder = Reminder.builder()
                .user(testUser)
                .task(task)
                .scheduledAt(scheduledTime)
                .status(ReminderStatus.PENDING)
                .build();
        reminderRepository.saveAndFlush(reminder);

        List<Reminder> dueReminders = reminderRepository.findDueRemindersWithDetails(
                ReminderStatus.PENDING, Instant.now()
        );

        assertThat(dueReminders).isNotEmpty();
        assertThat(dueReminders.get(0).getTask().getTitle()).isEqualTo("Upcoming Presentation");
        assertThat(dueReminders.get(0).getUser().getEmail()).isEqualTo("jane.doe@example.com");
    }

    @Test
    @DisplayName("Tokens persistence, hashing, and expiration checks")
    void testTokenPersistenceAndValidation() {
        RefreshToken refreshToken = RefreshToken.builder()
                .user(testUser)
                .tokenHash("a1b2c3d4e5f678901234567890abcdef1234567890abcdef1234567890abcdef")
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();
        refreshTokenRepository.saveAndFlush(refreshToken);

        EmailVerificationToken emailToken = EmailVerificationToken.builder()
                .user(testUser)
                .tokenHash("b2c3d4e5f678901234567890abcdef1234567890abcdef1234567890abcdef12")
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();
        emailVerificationTokenRepository.saveAndFlush(emailToken);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(testUser)
                .tokenHash("c3d4e5f678901234567890abcdef1234567890abcdef1234567890abcdef1234")
                .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS)) // expired
                .build();
        passwordResetTokenRepository.saveAndFlush(resetToken);

        assertThat(refreshToken.isValid()).isTrue();
        assertThat(emailToken.isValid()).isTrue();
        assertThat(resetToken.isExpired()).isTrue();
        assertThat(resetToken.isValid()).isFalse();
    }
}
