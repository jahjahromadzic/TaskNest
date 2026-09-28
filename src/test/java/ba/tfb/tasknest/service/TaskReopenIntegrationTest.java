package ba.tfb.tasknest.service;

import ba.tfb.tasknest.AbstractIntegrationTest;
import ba.tfb.tasknest.dto.auth.RegisterRequest;
import ba.tfb.tasknest.dto.offer.CreateOfferRequest;
import ba.tfb.tasknest.dto.task.CreateTaskRequest;
import ba.tfb.tasknest.entity.Category;
import ba.tfb.tasknest.entity.Conversation;
import ba.tfb.tasknest.entity.Municipality;
import ba.tfb.tasknest.entity.Notification;
import ba.tfb.tasknest.entity.Offer;
import ba.tfb.tasknest.entity.Task;
import ba.tfb.tasknest.entity.TaskerProfile;
import ba.tfb.tasknest.entity.User;
import ba.tfb.tasknest.entity.enums.ConversationStatus;
import ba.tfb.tasknest.entity.enums.NotificationType;
import ba.tfb.tasknest.entity.enums.OfferStatus;
import ba.tfb.tasknest.entity.enums.TaskStatus;
import ba.tfb.tasknest.exception.BusinessRuleException;
import ba.tfb.tasknest.exception.NotResourceOwnerException;
import ba.tfb.tasknest.repository.CategoryRepository;
import ba.tfb.tasknest.repository.ConversationRepository;
import ba.tfb.tasknest.repository.MunicipalityRepository;
import ba.tfb.tasknest.repository.NotificationRepository;
import ba.tfb.tasknest.repository.OfferRepository;
import ba.tfb.tasknest.repository.TaskRepository;
import ba.tfb.tasknest.repository.TaskerProfileRepository;
import ba.tfb.tasknest.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskReopenIntegrationTest extends AbstractIntegrationTest {

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
    @Autowired private NotificationRepository notificationRepository;

    private Category category;
    private Municipality municipality;
    private UUID clientId;
    private UUID emirId;
    private UUID mirzaId;
    private UUID taskId;
    private UUID emirOfferId;
    private UUID mirzaOfferId;

    @BeforeEach
    void setUp() {
        category = categoryRepository.findAll().getFirst();
        municipality = municipalityRepository.findAll().getFirst();

        clientId = register("reopen.client@test.ba");
        emirId = register("reopen.emir@test.ba");
        mirzaId = register("reopen.mirza@test.ba");
        authService.activateTaskerRole(emirId);
        authService.activateTaskerRole(mirzaId);

        taskId = taskService.createTask(clientId, new CreateTaskRequest(
                "Popravka slavine", "Curi ispod sudopera",
                category.getId(), municipality.getId(), new BigDecimal("80.00"))).id();
        taskService.publishTask(taskId, clientId);

        emirOfferId = submitOffer(emirId);
        mirzaOfferId = submitOffer(mirzaId);
        offerService.acceptOffer(emirOfferId, clientId);
    }

    @Test
    @DisplayName("When the assigned tasker withdraws, the task reopens and earlier offers become active again")
    void taskerWithdrawal_reopensTheTask_andRevivesEarlierOffers() {
        // Act
        offerService.withdrawOffer(emirOfferId, emirId);

        // Assert
        Task task = taskRepository.findById(taskId).orElseThrow();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.PUBLISHED);
        assertThat(task.getAcceptedOffer()).isNull();
        assertThat(offerStatus(emirOfferId)).isEqualTo(OfferStatus.WITHDRAWN);
        assertThat(offerStatus(mirzaOfferId)).isEqualTo(OfferStatus.PENDING);
        assertThat(conversationStatus(emirOfferId)).isEqualTo(ConversationStatus.ARCHIVED);
        assertThat(conversationStatus(mirzaOfferId)).isEqualTo(ConversationStatus.OPEN);
    }

    @Test
    @DisplayName("After a withdrawal the client can accept another earlier offer straight away")
    void afterWithdrawal_theClientCanAcceptAnotherOffer() {
        // Arrange
        offerService.withdrawOffer(emirOfferId, emirId);

        // Act
        offerService.acceptOffer(mirzaOfferId, clientId);

        // Assert
        Task task = transactionTemplate.execute(status -> {
            Task loaded = taskRepository.findById(taskId).orElseThrow();
            loaded.getAcceptedOffer().getId();
            return loaded;
        });
        assertThat(task.getStatus()).isEqualTo(TaskStatus.ASSIGNED);
        assertThat(task.getAcceptedOffer().getId()).isEqualTo(mirzaOfferId);
    }

    @Test
    @DisplayName("A tasker's own withdrawal counts on their profile and notifies both sides")
    void taskerWithdrawal_isCounted_andNotified() {
        // Act
        offerService.withdrawOffer(emirOfferId, emirId);

        // Assert
        assertThat(withdrawnJobs(emirId)).isEqualTo(1);
        assertThat(notificationTypes(clientId)).contains(NotificationType.TASKER_WITHDREW);
        assertThat(notificationTypes(mirzaId)).contains(NotificationType.OFFER_REACTIVATED);
    }

    @Test
    @DisplayName("When the client releases the tasker, nothing counts against the tasker")
    void clientRelease_isNotCountedAgainstTheTasker() {
        // Act
        taskService.reopenTask(taskId, clientId);

        // Assert
        assertThat(offerStatus(emirOfferId)).isEqualTo(OfferStatus.REJECTED);
        assertThat(offerStatus(mirzaOfferId)).isEqualTo(OfferStatus.PENDING);
        assertThat(withdrawnJobs(emirId)).isZero();
        assertThat(notificationTypes(emirId)).contains(NotificationType.ASSIGNMENT_RELEASED);
    }

    @Test
    @DisplayName("A tasker who dropped out cannot offer again on the same task")
    void droppedTasker_cannotOfferAgain() {
        // Arrange
        offerService.withdrawOffer(emirOfferId, emirId);

        // Act + Assert
        assertThatThrownBy(() -> submitOffer(emirId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already submitted");
    }

    @Test
    @DisplayName("A task whose work has started cannot be reopened")
    void reopen_isRejected_whenWorkHasStarted() {
        // Arrange
        taskService.startTask(taskId, emirId);

        // Act + Assert
        assertThatThrownBy(() -> taskService.reopenTask(taskId, clientId))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(taskRepository.findById(taskId).orElseThrow().getStatus())
                .isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("Reopening starts a fresh publication window, so a stale deadline does not expire the task")
    void reopen_startsAFreshPublicationWindow() {
        // Arrange
        transactionTemplate.executeWithoutResult(status ->
                taskRepository.findById(taskId).orElseThrow()
                        .setExpiresAt(LocalDateTime.now().minusDays(1)));

        // Act
        taskService.reopenTask(taskId, clientId);
        int expired = taskService.expireOverdueTasks();

        // Assert
        assertThat(expired).isZero();
        Task task = taskRepository.findById(taskId).orElseThrow();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.PUBLISHED);
        assertThat(task.getExpiresAt()).isAfter(LocalDateTime.now().plusDays(29));
    }

    @Test
    @DisplayName("Starting work and reopening at the same moment leave one consistent outcome")
    void concurrentStartAndReopen_leaveOneConsistentOutcome() throws Exception {
        // Act
        AtomicInteger successes = new AtomicInteger();
        Queue<Throwable> failures = runInParallel(
                () -> taskService.startTask(taskId, emirId),
                () -> taskService.reopenTask(taskId, clientId),
                successes);

        // Assert
        assertThat(successes.get()).isEqualTo(1);
        assertThat(failures).singleElement().isInstanceOfAny(
                ObjectOptimisticLockingFailureException.class,
                BusinessRuleException.class,
                NotResourceOwnerException.class);

        Task task = taskRepository.findById(taskId).orElseThrow();
        if (task.getStatus() == TaskStatus.IN_PROGRESS) {
            assertThat(offerStatus(emirOfferId)).isEqualTo(OfferStatus.ACCEPTED);
            assertThat(offerStatus(mirzaOfferId)).isEqualTo(OfferStatus.REJECTED);
        } else {
            assertThat(task.getStatus()).isEqualTo(TaskStatus.PUBLISHED);
            assertThat(offerStatus(emirOfferId)).isEqualTo(OfferStatus.REJECTED);
            assertThat(offerStatus(mirzaOfferId)).isEqualTo(OfferStatus.PENDING);
        }
    }

    private Queue<Throwable> runInParallel(Runnable first, Runnable second, AtomicInteger successes)
            throws Exception {
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(2);
        Queue<Throwable> failures = new ConcurrentLinkedQueue<>();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        for (Runnable action : List.of(first, second)) {
            pool.submit(() -> {
                try {
                    startSignal.await();
                    action.run();
                    successes.incrementAndGet();
                } catch (Throwable t) {
                    failures.add(t);
                } finally {
                    finished.countDown();
                }
            });
        }

        startSignal.countDown();
        assertThat(finished.await(15, TimeUnit.SECONDS)).as("threads did not finish in time").isTrue();
        pool.shutdown();

        return failures;
    }

    private UUID submitOffer(UUID taskerId) {
        return offerService.submitOffer(taskId, taskerId,
                new CreateOfferRequest(new BigDecimal("75.00"), "Mogu danas")).id();
    }

    private OfferStatus offerStatus(UUID offerId) {
        return offerRepository.findById(offerId).map(Offer::getStatus).orElseThrow();
    }

    private ConversationStatus conversationStatus(UUID offerId) {
        Offer offer = offerRepository.findById(offerId).orElseThrow();
        return conversationRepository.findByOffer(offer).map(Conversation::getStatus).orElseThrow();
    }

    private int withdrawnJobs(UUID taskerId) {
        User tasker = userRepository.findById(taskerId).orElseThrow();
        return taskerProfileRepository.findByUser(tasker)
                .map(TaskerProfile::getWithdrawnJobsCount)
                .orElseThrow();
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
