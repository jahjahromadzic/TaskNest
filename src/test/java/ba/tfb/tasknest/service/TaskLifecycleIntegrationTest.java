package ba.tfb.tasknest.service;

import ba.tfb.tasknest.AbstractIntegrationTest;
import ba.tfb.tasknest.dto.auth.AuthResponse;
import ba.tfb.tasknest.dto.auth.RegisterRequest;
import ba.tfb.tasknest.dto.offer.CreateOfferRequest;
import ba.tfb.tasknest.dto.task.CreateTaskRequest;
import ba.tfb.tasknest.dto.task.TaskResponse;
import ba.tfb.tasknest.domain.InvalidTaskTransitionException;
import ba.tfb.tasknest.entity.Category;
import ba.tfb.tasknest.entity.Municipality;
import ba.tfb.tasknest.entity.Notification;
import ba.tfb.tasknest.entity.TaskerProfile;
import ba.tfb.tasknest.entity.User;
import ba.tfb.tasknest.entity.enums.NotificationType;
import ba.tfb.tasknest.entity.enums.TaskStatus;
import ba.tfb.tasknest.exception.NotResourceOwnerException;
import ba.tfb.tasknest.repository.CategoryRepository;
import ba.tfb.tasknest.repository.ConversationRepository;
import ba.tfb.tasknest.repository.MunicipalityRepository;
import ba.tfb.tasknest.repository.NotificationRepository;
import ba.tfb.tasknest.repository.OfferRepository;
import ba.tfb.tasknest.repository.RefreshTokenRepository;
import ba.tfb.tasknest.repository.TaskRepository;
import ba.tfb.tasknest.repository.TaskerProfileRepository;
import ba.tfb.tasknest.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Izvrsenje posla protiv prave baze: ASSIGNED -> IN_PROGRESS -> COMPLETED -> CLOSED.
 * <p>
 * Unit testovi pokrivaju pravila; ovdje se provjerava ono sto se vidi samo kroz
 * bazu - brojac na profilu taskera (drugi agregat), primaoce notifikacija, i to
 * da kolona started_at iz migracije 033 stvarno postoji.
 * <p>
 * Arhiviranje razgovora se ovdje ne tvrdi: razgovore trenutno nista ne kreira, pa
 * je poziv nevidljiv u bazi. Da se poziva provjerava unit test.
 */
class TaskLifecycleIntegrationTest extends AbstractIntegrationTest {

    @Autowired private AuthService authService;
    @Autowired private TaskService taskService;
    @Autowired private OfferService offerService;
    @Autowired private TransactionTemplate transactionTemplate;

    @Autowired private UserRepository userRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private OfferRepository offerRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private MunicipalityRepository municipalityRepository;
    @Autowired private TaskerProfileRepository taskerProfileRepository;
    @Autowired private ConversationRepository conversationRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private NotificationRepository notificationRepository;

    private Category category;
    private Municipality municipality;
    private UUID clientId;
    private UUID taskerId;

    @BeforeEach
    void setUp() {
        category = categoryRepository.findAll().getFirst();
        municipality = municipalityRepository.findAll().getFirst();

        clientId = register("lifecycle.client@test.ba").userId();
        taskerId = register("lifecycle.tasker@test.ba").userId();
        authService.activateTaskerRole(taskerId);
    }

    @AfterEach
    void tearDown() {
        notificationRepository.deleteAll();
        conversationRepository.deleteAll();

        // tasks.accepted_offer_id i offers.task_id pokazuju jedno na drugo, pa
        // nijedna tabela ne moze biti prva. Veza se prvo raskine, pa se brise.
        transactionTemplate.executeWithoutResult(status ->
                taskRepository.findAll().forEach(task -> task.setAcceptedOffer(null)));

        offerRepository.deleteAll();
        taskRepository.deleteAll();
        taskerProfileRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("A task walks from assigned through in progress and completed to closed")
    void workflow_movesTaskThroughEveryState_untilClosed() {
        // Arrange
        UUID taskId = assignTaskToTasker();

        // Act + Assert - svaki prelaz vraca novo stanje
        assertThat(taskService.startTask(taskId, taskerId).status())
                .isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(taskService.completeTask(taskId, taskerId).status())
                .isEqualTo(TaskStatus.COMPLETED);
        assertThat(taskService.closeTask(taskId, clientId).status())
                .isEqualTo(TaskStatus.CLOSED);

        // Assert - i stanje je zaista u bazi, ne samo u odgovoru
        assertThat(taskRepository.findById(taskId).orElseThrow().getStatus())
                .isEqualTo(TaskStatus.CLOSED);
    }

    @Test
    @DisplayName("Both timestamps are persisted as work progresses")
    void workflow_persistsStartedAtAndCompletedAt() {
        // Arrange
        UUID taskId = assignTaskToTasker();

        // Act
        TaskResponse started = taskService.startTask(taskId, taskerId);
        TaskResponse completed = taskService.completeTask(taskId, taskerId);

        // Assert - started_at dolazi iz migracije 033; bez nje bi kontekst pao
        assertThat(started.startedAt()).isNotNull();
        assertThat(completed.completedAt()).isNotNull();
        assertThat(completed.completedAt()).isAfterOrEqualTo(completed.startedAt());
    }

    @Test
    @DisplayName("Closing a task credits the tasker, completing it does not")
    void closeTask_isTheOnlyTransitionThatRaisesTheCompletedJobsCount() {
        // Arrange
        UUID taskId = assignTaskToTasker();
        assertThat(completedJobsCount()).isZero();

        // Act - tasker prijavljuje zavrsetak
        taskService.startTask(taskId, taskerId);
        taskService.completeTask(taskId, taskerId);

        // Assert - sama prijava ne donosi nista, jer je tasker postavlja sam
        assertThat(completedJobsCount()).isZero();

        // Act - klijent potvrdjuje
        taskService.closeTask(taskId, clientId);

        // Assert
        assertThat(completedJobsCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Each transition notifies the other party")
    void workflow_notifiesTheOtherPartyOnEveryTransition() {
        // Arrange
        UUID taskId = assignTaskToTasker();

        // Act
        taskService.startTask(taskId, taskerId);
        taskService.completeTask(taskId, taskerId);
        taskService.closeTask(taskId, clientId);

        // Assert - klijent je obavijesten o pocetku i zavrsetku
        assertThat(notificationsFor(clientId))
                .extracting(Notification::getType)
                .containsExactlyInAnyOrder(
                        NotificationType.TASK_STARTED, NotificationType.TASK_COMPLETED);

        // Assert - tasker o zatvaranju. Notifikacija o prihvacenoj ponudi ne
        // postoji, pa je ovo jedina koju tasker ima.
        assertThat(notificationsFor(taskerId))
                .extracting(Notification::getType)
                .containsExactly(NotificationType.TASK_CLOSED);
    }

    @Test
    @DisplayName("Work cannot be completed before it is started")
    void completeTask_isRejected_whenWorkWasNeverStarted() {
        // Arrange
        UUID taskId = assignTaskToTasker();

        // Act + Assert
        assertThatThrownBy(() -> taskService.completeTask(taskId, taskerId))
                .isInstanceOf(InvalidTaskTransitionException.class);
    }

    @Test
    @DisplayName("The tasker cannot close their own work")
    void closeTask_isRejected_whenCalledByTheTasker() {
        // Arrange - asimetrija: tasker prijavljuje, klijent potvrdjuje
        UUID taskId = assignTaskToTasker();
        taskService.startTask(taskId, taskerId);
        taskService.completeTask(taskId, taskerId);

        // Act + Assert
        assertThatThrownBy(() -> taskService.closeTask(taskId, taskerId))
                .isInstanceOf(NotResourceOwnerException.class);

        // Assert - i brojac je ostao nedirnut
        assertThat(completedJobsCount()).isZero();
    }

    @Test
    @DisplayName("The client cannot start work on their own task")
    void startTask_isRejected_whenCalledByTheClient() {
        // Arrange
        UUID taskId = assignTaskToTasker();

        // Act + Assert
        assertThatThrownBy(() -> taskService.startTask(taskId, clientId))
                .isInstanceOf(NotResourceOwnerException.class);
    }

    @Test
    @DisplayName("A tasker who was not assigned the task cannot start it")
    void startTask_isRejected_whenCalledByAnotherTasker() {
        // Arrange
        UUID taskId = assignTaskToTasker();
        UUID outsiderId = register("lifecycle.outsider@test.ba").userId();
        authService.activateTaskerRole(outsiderId);

        // Act + Assert - rola TASKER nije dovoljna, mora biti dodijeljeni tasker
        assertThatThrownBy(() -> taskService.startTask(taskId, outsiderId))
                .isInstanceOf(NotResourceOwnerException.class);
    }

    // ---------- helpers ----------

    /** Vodi task do ASSIGNED: kreiraj, objavi, ponudi, prihvati. */
    private UUID assignTaskToTasker() {
        UUID taskId = taskService.createTask(clientId, new CreateTaskRequest(
                "Popravka slavine", "Curi ispod sudopera",
                category.getId(), municipality.getId(), new BigDecimal("80.00"))).id();
        taskService.publishTask(taskId, clientId);

        UUID offerId = offerService.submitOffer(taskId, taskerId,
                new CreateOfferRequest(new BigDecimal("75.00"), "Mogu danas")).id();
        offerService.acceptOffer(offerId, clientId);

        return taskId;
    }

    private int completedJobsCount() {
        User tasker = userRepository.findById(taskerId).orElseThrow();
        return taskerProfileRepository.findByUser(tasker)
                .map(TaskerProfile::getCompletedJobsCount)
                .orElseThrow();
    }

    private List<Notification> notificationsFor(UUID userId) {
        User recipient = userRepository.findById(userId).orElseThrow();
        return notificationRepository.findByRecipientOrderByCreatedAtDesc(recipient);
    }

    private AuthResponse register(String email) {
        return authService.register(new RegisterRequest(email, "password123", "Test", "User", null));
    }
}
