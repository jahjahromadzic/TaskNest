package ba.tfb.tasknest.messaging;

import ba.tfb.tasknest.AbstractIntegrationTest;
import ba.tfb.tasknest.dto.auth.AuthResponse;
import ba.tfb.tasknest.dto.auth.RegisterRequest;
import ba.tfb.tasknest.dto.task.CreateTaskRequest;
import ba.tfb.tasknest.entity.Category;
import ba.tfb.tasknest.entity.Municipality;
import ba.tfb.tasknest.entity.Notification;
import ba.tfb.tasknest.entity.User;
import ba.tfb.tasknest.entity.enums.NotificationType;
import ba.tfb.tasknest.repository.*;
import ba.tfb.tasknest.service.AuthService;
import ba.tfb.tasknest.service.TaskService;
import ba.tfb.tasknest.service.TaskerProfileService;
import ba.tfb.tasknest.dto.taskerprofile.UpdateCoverageRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Cijeli asinhroni tok protiv pravog RabbitMQ brokera: objava oglasa, poruka na
 * exchange, potrosac, upisana notifikacija.
 * <p>
 * Tvrdnje su u await bloku jer se obrada desava u drugoj niti nakon commita -
 * u trenutku kad publishTask vrati odgovor, notifikacija jos ne postoji.
 */
class TaskPublishedNotificationTest extends AbstractIntegrationTest {

    @Autowired private AuthService authService;
    @Autowired private TaskService taskService;
    @Autowired private TaskerProfileService taskerProfileService;

    @Autowired private UserRepository userRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private MunicipalityRepository municipalityRepository;
    @Autowired private TaskerProfileRepository taskerProfileRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private NotificationRepository notificationRepository;

    private Category category;
    private Municipality municipality;
    private UUID clientId;

    @BeforeEach
    void setUp() {
        category = categoryRepository.findAll().getFirst();
        municipality = municipalityRepository.findAll().getFirst();
        clientId = register("notify.client@test.ba").userId();
    }

    @AfterEach
    void tearDown() {
        notificationRepository.deleteAll();
        taskRepository.deleteAll();
        taskerProfileRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Publishing a task notifies every tasker covering its category and municipality")
    void publishTask_notifiesCoveringTaskers() {
        // Arrange - tasker koji pokriva bas tu kategoriju i opstinu
        UUID taskerId = registerTaskerCovering(
                "notify.tasker@test.ba", category.getId(), municipality.getId());

        // Act
        UUID taskId = publishTask("Popravka slavine");

        // Assert - obrada je asinhrona, pa se ceka da poruka prodje kroz broker
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            List<Notification> notifications = notificationRepository.findAll();

            assertThat(notifications).hasSize(1);
            Notification notification = notifications.getFirst();
            assertThat(notification.getRecipient().getId()).isEqualTo(taskerId);
            assertThat(notification.getType()).isEqualTo(NotificationType.NEW_TASK_IN_AREA);
            assertThat(notification.getRelatedEntityId()).isEqualTo(taskId);
            assertThat(notification.getContent()).contains("Popravka slavine");
            assertThat(notification.isRead()).isFalse();
        });
    }

    @Test
    @DisplayName("A tasker covering a different municipality is not notified")
    void publishTask_doesNotNotifyTaskersOutsideTheArea() {
        // Arrange - tasker pokriva istu kategoriju, ali drugu opstinu
        Municipality elsewhere = municipalityRepository.findAll().get(1);
        registerTaskerCovering("notify.elsewhere@test.ba", category.getId(), elsewhere.getId());

        // Act
        publishTask("Popravka slavine");

        // Assert - nema sta da stigne; kratko cekanje da se potvrdi da ostaje prazno
        await().during(Duration.ofSeconds(3))
                .atMost(Duration.ofSeconds(6))
                .untilAsserted(() -> assertThat(notificationRepository.findAll()).isEmpty());
    }

    @Test
    @DisplayName("Creating a draft sends no notification; only publishing does")
    void createTask_sendsNoNotification_whileTaskIsStillADraft() {
        // Arrange
        registerTaskerCovering("notify.draft@test.ba", category.getId(), municipality.getId());

        // Act - samo kreiranje, bez objave
        taskService.createTask(clientId, new CreateTaskRequest(
                "Nacrt", null, category.getId(), municipality.getId(), new BigDecimal("50.00")));

        // Assert
        await().during(Duration.ofSeconds(3))
                .atMost(Duration.ofSeconds(6))
                .untilAsserted(() -> assertThat(notificationRepository.findAll()).isEmpty());
    }

    // ---------- helpers ----------

    private UUID publishTask(String title) {
        UUID taskId = taskService.createTask(clientId, new CreateTaskRequest(
                title, "Opis", category.getId(), municipality.getId(), new BigDecimal("50.00"))).id();
        taskService.publishTask(taskId, clientId);
        return taskId;
    }

    private UUID registerTaskerCovering(String email, UUID categoryId, UUID municipalityId) {
        AuthResponse tasker = register(email);
        authService.activateTaskerRole(tasker.userId());
        taskerProfileService.updateCategories(tasker.userId(), new UpdateCoverageRequest(Set.of(categoryId)));
        taskerProfileService.updateMunicipalities(tasker.userId(), new UpdateCoverageRequest(Set.of(municipalityId)));
        return tasker.userId();
    }

    private AuthResponse register(String email) {
        return authService.register(new RegisterRequest(email, "password123", "Test", "User", null));
    }
}
