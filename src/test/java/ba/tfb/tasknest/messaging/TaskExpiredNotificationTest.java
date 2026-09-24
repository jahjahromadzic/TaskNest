package ba.tfb.tasknest.messaging;

import ba.tfb.tasknest.AbstractIntegrationTest;
import ba.tfb.tasknest.dto.auth.AuthResponse;
import ba.tfb.tasknest.dto.auth.RegisterRequest;
import ba.tfb.tasknest.dto.task.CreateTaskRequest;
import ba.tfb.tasknest.entity.Category;
import ba.tfb.tasknest.entity.Municipality;
import ba.tfb.tasknest.entity.Notification;
import ba.tfb.tasknest.entity.Task;
import ba.tfb.tasknest.entity.enums.NotificationType;
import ba.tfb.tasknest.entity.enums.TaskStatus;
import ba.tfb.tasknest.repository.CategoryRepository;
import ba.tfb.tasknest.repository.MunicipalityRepository;
import ba.tfb.tasknest.repository.NotificationRepository;
import ba.tfb.tasknest.repository.RefreshTokenRepository;
import ba.tfb.tasknest.repository.TaskRepository;
import ba.tfb.tasknest.repository.UserRepository;
import ba.tfb.tasknest.service.AuthService;
import ba.tfb.tasknest.service.TaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Istek oglasa od kraja do kraja: servis prebaci status, dogadjaj prodje kroz
 * pravi RabbitMQ, potrosac upise notifikaciju vlasniku.
 * <p>
 * Raspored je u test profilu ugasen (app.task-expiry.enabled=false) i servis se
 * zove direktno - inace bi pozadinska nit mijenjala podatke ispod testa.
 */
class TaskExpiredNotificationTest extends AbstractIntegrationTest {

    @Autowired private AuthService authService;
    @Autowired private TaskService taskService;
    @Autowired private TransactionTemplate transactionTemplate;

    @Autowired private UserRepository userRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private MunicipalityRepository municipalityRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private NotificationRepository notificationRepository;

    private Category category;
    private Municipality municipality;
    private UUID clientId;

    @BeforeEach
    void setUp() {
        category = categoryRepository.findAll().getFirst();
        municipality = municipalityRepository.findAll().getFirst();
        clientId = register("expiry.client@test.ba").userId();
    }

    @AfterEach
    void tearDown() {
        notificationRepository.deleteAll();
        taskRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("An overdue published task expires and its owner is notified")
    void expireOverdueTasks_notifiesOwner_whenDeadlineHasPassed() {
        // Arrange - objavljen oglas kojem je rok prosao
        UUID taskId = publishTask("Popravka slavine");
        backdateDeadline(taskId, LocalDateTime.now().minusMinutes(1));

        // Act
        int expired = taskService.expireOverdueTasks();

        // Assert
        assertThat(expired).isEqualTo(1);
        assertThat(taskRepository.findById(taskId).orElseThrow().getStatus())
                .isEqualTo(TaskStatus.EXPIRED);

        // Poruka ide preko brokera, pa notifikacija stize u drugoj niti
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            List<Notification> notifications = notificationRepository.findAll();

            assertThat(notifications).hasSize(1);
            Notification notification = notifications.getFirst();
            assertThat(notification.getRecipient().getId()).isEqualTo(clientId);
            assertThat(notification.getType()).isEqualTo(NotificationType.TASK_EXPIRED);
            assertThat(notification.getRelatedEntityId()).isEqualTo(taskId);
            assertThat(notification.getContent()).contains("Popravka slavine");
            assertThat(notification.isRead()).isFalse();
        });
    }

    @Test
    @DisplayName("A task whose deadline has not passed is left published")
    void expireOverdueTasks_leavesTaskPublished_whenDeadlineIsInTheFuture() {
        // Arrange - svjeze objavljen oglas ima rok 30 dana u buducnosti
        UUID taskId = publishTask("Jos vazi");

        // Act
        int expired = taskService.expireOverdueTasks();

        // Assert
        assertThat(expired).isZero();
        assertThat(taskRepository.findById(taskId).orElseThrow().getStatus())
                .isEqualTo(TaskStatus.PUBLISHED);
        await().during(Duration.ofSeconds(3))
                .atMost(Duration.ofSeconds(6))
                .untilAsserted(() -> assertThat(notificationRepository.findAll()).isEmpty());
    }

    @Test
    @DisplayName("A draft is never expired, however old it is")
    void expireOverdueTasks_ignoresDrafts() {
        // Arrange - nacrt sa proslim rokom; rok bez objave ne bi trebao znaciti nista
        UUID taskId = taskService.createTask(clientId, new CreateTaskRequest(
                "Nacrt", "Opis", category.getId(), municipality.getId(), new BigDecimal("50.00"))).id();
        backdateDeadline(taskId, LocalDateTime.now().minusDays(60));

        // Act
        int expired = taskService.expireOverdueTasks();

        // Assert
        assertThat(expired).isZero();
        assertThat(taskRepository.findById(taskId).orElseThrow().getStatus())
                .isEqualTo(TaskStatus.DRAFT);
    }

    @Test
    @DisplayName("A second pass does not re-expire an already expired task")
    void expireOverdueTasks_isIdempotent_acrossRuns() {
        // Arrange
        UUID taskId = publishTask("Samo jednom");
        backdateDeadline(taskId, LocalDateTime.now().minusMinutes(1));
        taskService.expireOverdueTasks();
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(notificationRepository.findAll()).hasSize(1));

        // Act - raspored radi u krug; drugi prolaz ne smije slati opet
        int expired = taskService.expireOverdueTasks();

        // Assert
        assertThat(expired).isZero();
        await().during(Duration.ofSeconds(3))
                .atMost(Duration.ofSeconds(6))
                .untilAsserted(() -> assertThat(notificationRepository.findAll()).hasSize(1));
    }

    // ---------- helpers ----------

    private UUID publishTask(String title) {
        UUID taskId = taskService.createTask(clientId, new CreateTaskRequest(
                title, "Opis", category.getId(), municipality.getId(), new BigDecimal("50.00"))).id();
        taskService.publishTask(taskId, clientId);
        return taskId;
    }

    /**
     * Pomjera rok u proslost direktno u bazi. Servis nema metodu za to jer je rok
     * izveden iz trenutka objave - a test ne smije cekati 30 dana.
     */
    private void backdateDeadline(UUID taskId, LocalDateTime deadline) {
        transactionTemplate.executeWithoutResult(status -> {
            Task task = taskRepository.findById(taskId).orElseThrow();
            task.setExpiresAt(deadline);
        });
    }

    private AuthResponse register(String email) {
        return authService.register(new RegisterRequest(email, "password123", "Test", "User", null));
    }
}
