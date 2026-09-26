package ba.tfb.tasknest.scheduler;

import ba.tfb.tasknest.AbstractIntegrationTest;
import ba.tfb.tasknest.dto.auth.RegisterRequest;
import ba.tfb.tasknest.dto.offer.CreateOfferRequest;
import ba.tfb.tasknest.dto.task.CreateTaskRequest;
import ba.tfb.tasknest.entity.Category;
import ba.tfb.tasknest.entity.Municipality;
import ba.tfb.tasknest.entity.Notification;
import ba.tfb.tasknest.entity.Offer;
import ba.tfb.tasknest.entity.Task;
import ba.tfb.tasknest.entity.TaskerProfile;
import ba.tfb.tasknest.entity.User;
import ba.tfb.tasknest.entity.enums.NotificationType;
import ba.tfb.tasknest.entity.enums.OfferStatus;
import ba.tfb.tasknest.entity.enums.TaskStatus;
import ba.tfb.tasknest.repository.CategoryRepository;
import ba.tfb.tasknest.repository.ConversationRepository;
import ba.tfb.tasknest.repository.MessageRepository;
import ba.tfb.tasknest.repository.MunicipalityRepository;
import ba.tfb.tasknest.repository.NotificationRepository;
import ba.tfb.tasknest.repository.OfferRepository;
import ba.tfb.tasknest.repository.RefreshTokenRepository;
import ba.tfb.tasknest.repository.ReviewRepository;
import ba.tfb.tasknest.repository.TaskRepository;
import ba.tfb.tasknest.repository.TaskerProfileRepository;
import ba.tfb.tasknest.repository.UserRepository;
import ba.tfb.tasknest.service.AuthService;
import ba.tfb.tasknest.service.OfferService;
import ba.tfb.tasknest.service.TaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class TaskDeadlineIntegrationTest extends AbstractIntegrationTest {

    private static final int ASSIGNMENT_START_DAYS = 14;
    private static final int COMPLETION_CLOSE_DAYS = 7;

    @Autowired private AuthService authService;
    @Autowired private TaskService taskService;
    @Autowired private OfferService offerService;
    @Autowired private Clock clock;
    @Autowired private TransactionTemplate transactionTemplate;

    @Autowired private UserRepository userRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private OfferRepository offerRepository;
    @Autowired private MessageRepository messageRepository;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private MunicipalityRepository municipalityRepository;
    @Autowired private TaskerProfileRepository taskerProfileRepository;
    @Autowired private ConversationRepository conversationRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private NotificationRepository notificationRepository;

    private TaskDeadlineSchedule schedule;
    private Category category;
    private Municipality municipality;
    private UUID clientId;
    private UUID emirId;
    private UUID mirzaId;

    @BeforeEach
    void setUp() {
        schedule = new TaskDeadlineSchedule(taskService, clock, ASSIGNMENT_START_DAYS, COMPLETION_CLOSE_DAYS);
        category = categoryRepository.findAll().getFirst();
        municipality = municipalityRepository.findAll().getFirst();

        clientId = register("deadline.client@test.ba");
        emirId = register("deadline.emir@test.ba");
        mirzaId = register("deadline.mirza@test.ba");
        authService.activateTaskerRole(emirId);
        authService.activateTaskerRole(mirzaId);
    }

    @AfterEach
    void tearDown() {
        messageRepository.deleteAll();
        reviewRepository.deleteAll();
        notificationRepository.deleteAll();
        conversationRepository.deleteAll();
        transactionTemplate.executeWithoutResult(status ->
                taskRepository.findAll().forEach(task -> task.setAcceptedOffer(null)));
        offerRepository.deleteAll();
        taskRepository.deleteAll();
        taskerProfileRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("An assigned task that never starts is reopened after the deadline, without penalising the tasker")
    void staleAssignment_isReopened() {
        // Arrange
        AssignedTask assigned = assignToEmir();
        shift(assigned.taskId(), task -> task.setAssignedAt(daysAgo(ASSIGNMENT_START_DAYS + 1)));

        // Act
        schedule.enforceDeadlines();

        // Assert
        Task task = taskRepository.findById(assigned.taskId()).orElseThrow();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.PUBLISHED);
        assertThat(task.getAssignedAt()).isNull();
        assertThat(offerStatus(assigned.emirOfferId())).isEqualTo(OfferStatus.REJECTED);
        assertThat(offerStatus(assigned.mirzaOfferId())).isEqualTo(OfferStatus.PENDING);
        assertThat(profile(emirId).getWithdrawnJobsCount()).isZero();
        assertThat(notificationTypes(clientId)).contains(NotificationType.ASSIGNMENT_EXPIRED);
        assertThat(notificationTypes(emirId)).contains(NotificationType.ASSIGNMENT_EXPIRED);
        assertThat(notificationTypes(mirzaId)).contains(NotificationType.OFFER_REACTIVATED);
    }

    @Test
    @DisplayName("An assignment still within its deadline is left alone")
    void recentAssignment_isLeftAlone() {
        // Arrange
        AssignedTask assigned = assignToEmir();
        shift(assigned.taskId(), task -> task.setAssignedAt(daysAgo(ASSIGNMENT_START_DAYS - 1)));

        // Act
        schedule.enforceDeadlines();

        // Assert
        assertThat(taskRepository.findById(assigned.taskId()).orElseThrow().getStatus())
                .isEqualTo(TaskStatus.ASSIGNED);
    }

    @Test
    @DisplayName("Work that has started is never reopened, however long it takes")
    void startedWork_isNeverReopened() {
        // Arrange
        AssignedTask assigned = assignToEmir();
        taskService.startTask(assigned.taskId(), emirId);
        shift(assigned.taskId(), task -> task.setAssignedAt(daysAgo(60)));

        // Act
        schedule.enforceDeadlines();

        // Assert
        assertThat(taskRepository.findById(assigned.taskId()).orElseThrow().getStatus())
                .isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("A completed task the client never closes is closed after the deadline and credited to the tasker")
    void staleCompletion_isClosedAndCredited() {
        // Arrange
        AssignedTask assigned = assignToEmir();
        taskService.startTask(assigned.taskId(), emirId);
        taskService.completeTask(assigned.taskId(), emirId);
        shift(assigned.taskId(), task -> task.setCompletedAt(daysAgo(COMPLETION_CLOSE_DAYS + 1)));

        // Act
        schedule.enforceDeadlines();

        // Assert
        assertThat(taskRepository.findById(assigned.taskId()).orElseThrow().getStatus())
                .isEqualTo(TaskStatus.CLOSED);
        assertThat(profile(emirId).getCompletedJobsCount()).isEqualTo(1);
        assertThat(notificationTypes(clientId)).contains(NotificationType.TASK_AUTO_CLOSED);
        assertThat(notificationTypes(emirId)).contains(NotificationType.TASK_AUTO_CLOSED);
    }

    @Test
    @DisplayName("A recently completed task waits for the client")
    void recentCompletion_waitsForTheClient() {
        // Arrange
        AssignedTask assigned = assignToEmir();
        taskService.startTask(assigned.taskId(), emirId);
        taskService.completeTask(assigned.taskId(), emirId);

        // Act
        schedule.enforceDeadlines();

        // Assert
        assertThat(taskRepository.findById(assigned.taskId()).orElseThrow().getStatus())
                .isEqualTo(TaskStatus.COMPLETED);
        assertThat(profile(emirId).getCompletedJobsCount()).isZero();
    }

    @Test
    @DisplayName("A task that fails to process does not stop the others")
    void failingTask_doesNotStopTheOthers() {
        // Arrange
        AssignedTask broken = assignToEmir();
        AssignedTask healthy = assignToEmir();
        shift(broken.taskId(), task -> {
            task.setAssignedAt(daysAgo(ASSIGNMENT_START_DAYS + 1));
            task.setAcceptedOffer(null);
        });
        shift(healthy.taskId(), task -> task.setAssignedAt(daysAgo(ASSIGNMENT_START_DAYS + 1)));

        // Act
        schedule.enforceDeadlines();

        // Assert
        assertThat(taskRepository.findById(broken.taskId()).orElseThrow().getStatus())
                .isEqualTo(TaskStatus.ASSIGNED);
        assertThat(taskRepository.findById(healthy.taskId()).orElseThrow().getStatus())
                .isEqualTo(TaskStatus.PUBLISHED);
    }

    @Test
    @DisplayName("After a reopen nothing hangs: an untouched task still expires through the normal rule")
    void reopenedTask_stillExpiresLater() {
        // Arrange
        AssignedTask assigned = assignToEmir();
        shift(assigned.taskId(), task -> task.setAssignedAt(daysAgo(ASSIGNMENT_START_DAYS + 1)));
        schedule.enforceDeadlines();
        shift(assigned.taskId(), task -> task.setExpiresAt(daysAgo(1)));

        // Act
        taskService.expireOverdueTasks();

        // Assert
        assertThat(taskRepository.findById(assigned.taskId()).orElseThrow().getStatus())
                .isEqualTo(TaskStatus.EXPIRED);
    }

    private record AssignedTask(UUID taskId, UUID emirOfferId, UUID mirzaOfferId) {
    }

    private AssignedTask assignToEmir() {
        UUID taskId = taskService.createTask(clientId, new CreateTaskRequest(
                "Popravka slavine", "Curi ispod sudopera",
                category.getId(), municipality.getId(), new BigDecimal("80.00"))).id();
        taskService.publishTask(taskId, clientId);

        UUID emirOffer = offerService.submitOffer(taskId, emirId,
                new CreateOfferRequest(new BigDecimal("45.00"), "Mogu sutra")).id();
        UUID mirzaOffer = offerService.submitOffer(taskId, mirzaId,
                new CreateOfferRequest(new BigDecimal("60.00"), "Mogu danas")).id();
        offerService.acceptOffer(emirOffer, clientId);

        return new AssignedTask(taskId, emirOffer, mirzaOffer);
    }

    private void shift(UUID taskId, Consumer<Task> change) {
        transactionTemplate.executeWithoutResult(status ->
                change.accept(taskRepository.findById(taskId).orElseThrow()));
    }

    private LocalDateTime daysAgo(int days) {
        return LocalDateTime.now(clock).minusDays(days);
    }

    private OfferStatus offerStatus(UUID offerId) {
        return offerRepository.findById(offerId).map(Offer::getStatus).orElseThrow();
    }

    private TaskerProfile profile(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow();
        return taskerProfileRepository.findByUser(user).orElseThrow();
    }

    private List<NotificationType> notificationTypes(UUID userId) {
        User recipient = userRepository.findById(userId).orElseThrow();
        return notificationRepository.findByRecipientOrderByCreatedAtDesc(recipient).stream()
                .map(Notification::getType)
                .toList();
    }

    private UUID register(String email) {
        return authService.register(new RegisterRequest(email, "password123", "Test", "User", null)).userId();
    }
}
