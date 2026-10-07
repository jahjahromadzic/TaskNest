package ba.tfb.tasknest.bootstrap;

import ba.tfb.tasknest.AbstractIntegrationTest;
import ba.tfb.tasknest.dto.auth.AuthResponse;
import ba.tfb.tasknest.dto.auth.LoginRequest;
import ba.tfb.tasknest.entity.Task;
import ba.tfb.tasknest.entity.TaskerProfile;
import ba.tfb.tasknest.entity.User;
import ba.tfb.tasknest.entity.enums.TaskStatus;
import ba.tfb.tasknest.repository.NotificationRepository;
import ba.tfb.tasknest.repository.ReviewRepository;
import ba.tfb.tasknest.repository.TaskRepository;
import ba.tfb.tasknest.repository.TaskerProfileRepository;
import ba.tfb.tasknest.repository.UserRepository;
import ba.tfb.tasknest.service.AuthService;
import ba.tfb.tasknest.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
        "app.demo-data.enabled=true",
        "app.demo-data.password=demo-test-password"
})
class DemoDataSeederTest extends AbstractIntegrationTest {

    @Autowired private DemoDataSeeder seeder;
    @Autowired private UserRepository userRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private TaskerProfileRepository taskerProfileRepository;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private TaskService taskService;
    @Autowired private AuthService authService;
    @Autowired private TransactionTemplate transactionTemplate;

    @BeforeEach
    void seed() {
        seeder.seed();
    }

    @Test
    @DisplayName("Seeding twice does not create anything twice")
    void seed_isIdempotent() {
        long users = userRepository.count();
        long tasks = taskRepository.count();

        seeder.seed();

        assertThat(userRepository.count()).isEqualTo(users).isEqualTo(9);
        assertThat(taskRepository.count()).isEqualTo(tasks);
    }

    @Test
    @DisplayName("Every open demo task shows up in the public listing")
    void seed_fillsThePublicListing() {
        long open = taskService.browseTasks(null, null, PageRequest.of(0, 50)).getTotalElements();

        assertThat(open).isEqualTo(25);
    }

    @Test
    @DisplayName("The demo has a task in every status, so every tab and badge has something to show")
    void seed_coversEveryTaskStatus() {
        Set<TaskStatus> statuses = taskRepository.findAll().stream().map(Task::getStatus).collect(Collectors.toSet());

        assertThat(statuses).containsExactlyInAnyOrder(TaskStatus.values());
    }

    @Test
    @DisplayName("Clients have reviews from taskers and every demo user except the admin has notifications")
    void seed_fillsClientProfilesAndNotifications() {
        UUID amra = userRepository.findByEmail("amra" + DemoDataSeeder.EMAIL_DOMAIN).orElseThrow().getId();

        assertThat(reviewRepository.summarizeReceivedAsClient(amra).count()).isEqualTo(1);
        transactionTemplate.executeWithoutResult(status -> userRepository.findAll().stream()
                .filter(user -> !user.getEmail().startsWith("lejla"))
                .forEach(user -> assertThat(notificationRepository.findByRecipientOrderByCreatedAtDesc(user))
                        .as(user.getEmail()).isNotEmpty()));
    }

    @Test
    @DisplayName("A demo account can log in with the configured password")
    void seed_createsAccountsThatCanLogIn() {
        AuthResponse admin = authService.login(new LoginRequest("lejla" + DemoDataSeeder.EMAIL_DOMAIN, "demo-test-password"));

        assertThat(admin.roles()).contains("ADMIN");
    }

    @Test
    @DisplayName("Tasker ratings and job counts match the seeded reviews and closed tasks")
    void seed_keepsTaskerStatisticsConsistent() {
        transactionTemplate.executeWithoutResult(status -> {
            for (TaskerProfile profile : taskerProfileRepository.findAll()) {
                User tasker = profile.getUser();
                BigDecimal expected = reviewRepository.findAverageRatingAsTasker(tasker.getId())
                        .map(value -> BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP))
                        .orElse(null);
                long closedJobs = taskRepository.findAll().stream()
                        .filter(task -> task.getStatus() == TaskStatus.CLOSED)
                        .filter(task -> task.getAcceptedOffer().getTasker().equals(tasker))
                        .count();

                assertThat(profile.getAverageRating()).as(tasker.getEmail()).isEqualTo(expected);
                assertThat(profile.getCompletedJobsCount()).as(tasker.getEmail()).isEqualTo((int) closedJobs);
            }
        });
    }

    @Test
    @DisplayName("The schedulers find nothing to expire, reopen or close right after seeding")
    void seed_respectsTheDeadlines() {
        LocalDateTime now = LocalDateTime.now();

        assertThat(taskService.expireOverdueTasks()).isZero();
        assertThat(taskService.findTasksAssignedBefore(now.minusDays(14))).isEmpty();
        assertThat(taskService.findTasksCompletedBefore(now.minusDays(7))).isEmpty();
    }
}
