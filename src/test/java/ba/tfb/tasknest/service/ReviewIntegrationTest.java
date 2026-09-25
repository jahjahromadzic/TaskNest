package ba.tfb.tasknest.service;

import ba.tfb.tasknest.AbstractIntegrationTest;
import ba.tfb.tasknest.dto.auth.AuthResponse;
import ba.tfb.tasknest.dto.auth.RegisterRequest;
import ba.tfb.tasknest.dto.offer.CreateOfferRequest;
import ba.tfb.tasknest.dto.review.CreateReviewRequest;
import ba.tfb.tasknest.dto.task.CreateTaskRequest;
import ba.tfb.tasknest.entity.Category;
import ba.tfb.tasknest.entity.Municipality;
import ba.tfb.tasknest.entity.Notification;
import ba.tfb.tasknest.entity.TaskerProfile;
import ba.tfb.tasknest.entity.User;
import ba.tfb.tasknest.entity.enums.NotificationType;
import ba.tfb.tasknest.exception.BusinessRuleException;
import ba.tfb.tasknest.repository.CategoryRepository;
import ba.tfb.tasknest.repository.ConversationRepository;
import ba.tfb.tasknest.repository.MunicipalityRepository;
import ba.tfb.tasknest.repository.NotificationRepository;
import ba.tfb.tasknest.repository.OfferRepository;
import ba.tfb.tasknest.repository.RefreshTokenRepository;
import ba.tfb.tasknest.repository.ReviewRepository;
import ba.tfb.tasknest.repository.TaskRepository;
import ba.tfb.tasknest.repository.TaskerProfileRepository;
import ba.tfb.tasknest.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ocjene protiv prave baze.
 * <p>
 * Unit testovi pokrivaju ko smije ocijeniti koga; ovdje se provjerava ono sto
 * postoji samo u bazi - keširana prosjecna ocjena na profilu, jedinstvenost
 * (task, recenzent), i da paralelne ocjene istom taskeru ne pokvare prosjek.
 */
class ReviewIntegrationTest extends AbstractIntegrationTest {

    @Autowired private AuthService authService;
    @Autowired private TaskService taskService;
    @Autowired private OfferService offerService;
    @Autowired private ReviewService reviewService;
    @Autowired private TransactionTemplate transactionTemplate;

    @Autowired private UserRepository userRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private OfferRepository offerRepository;
    @Autowired private ReviewRepository reviewRepository;
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

        clientId = register("review.client@test.ba").userId();
        taskerId = register("review.tasker@test.ba").userId();
        authService.activateTaskerRole(taskerId);
    }

    @AfterEach
    void tearDown() {
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
    @DisplayName("A review caches the average rating on the tasker profile")
    void createReview_cachesTheAverage_whenTheRevieweeIsATasker() {
        // Arrange
        UUID taskId = closedTask();

        // Act
        reviewService.createReview(taskId, clientId, new CreateReviewRequest(5, "Odlicno"));

        // Assert - scale 2, jer je kolona DECIMAL(3,2)
        assertThat(cachedAverage()).isEqualByComparingTo("5.00");
    }

    @Test
    @DisplayName("The cached average is recomputed as more reviews arrive")
    void createReview_recomputesTheAverage_acrossSeveralTasks() {
        // Arrange - dva zatvorena posla istog taskera
        UUID firstTask = closedTask();
        UUID secondTask = closedTask();

        // Act
        reviewService.createReview(firstTask, clientId, new CreateReviewRequest(5, null));
        reviewService.createReview(secondTask, clientId, new CreateReviewRequest(4, null));

        // Assert
        assertThat(cachedAverage()).isEqualByComparingTo("4.50");
    }

    @Test
    @DisplayName("An average that does not divide evenly is rounded to two decimals")
    void createReview_roundsTheAverage_whenItDoesNotDivideEvenly() {
        // Arrange
        UUID first = closedTask();
        UUID second = closedTask();
        UUID third = closedTask();

        // Act - 13 / 3 = 4.333...
        reviewService.createReview(first, clientId, new CreateReviewRequest(5, null));
        reviewService.createReview(second, clientId, new CreateReviewRequest(4, null));
        reviewService.createReview(third, clientId, new CreateReviewRequest(4, null));

        // Assert
        assertThat(cachedAverage()).isEqualByComparingTo("4.33");
    }

    @Test
    @DisplayName("Both parties can review the same task, once each")
    void createReview_allowsOneReviewPerPartyOnTheSameTask() {
        // Arrange
        UUID taskId = closedTask();

        // Act - jedinstvenost je na (task, recenzent), pa dvije ocjene po poslu
        reviewService.createReview(taskId, clientId, new CreateReviewRequest(5, "Dobar majstor"));
        reviewService.createReview(taskId, taskerId, new CreateReviewRequest(4, "Korektan klijent"));

        // Assert
        assertThat(reviewRepository.findAll()).hasSize(2);
        assertThat(reviewRepository.findByReviewee(user(taskerId))).hasSize(1);
        assertThat(reviewRepository.findByReviewee(user(clientId))).hasSize(1);
    }

    @Test
    @DisplayName("Reviewing the same task twice is rejected")
    void createReview_isRejected_whenTheSameReviewerReviewsTwice() {
        // Arrange
        UUID taskId = closedTask();
        reviewService.createReview(taskId, clientId, new CreateReviewRequest(5, null));

        // Act + Assert
        assertThatThrownBy(() -> reviewService.createReview(
                taskId, clientId, new CreateReviewRequest(1, "Promijenio sam misljenje")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already reviewed");

        // Assert - prva ocjena je ostala nedirnuta
        assertThat(reviewRepository.findAll()).hasSize(1);
        assertThat(cachedAverage()).isEqualByComparingTo("5.00");
    }

    @Test
    @DisplayName("A review of a client is stored but cached nowhere, since clients have no profile")
    void createReview_storesButDoesNotCache_whenTheRevieweeIsAClient() {
        // Arrange
        UUID taskId = closedTask();

        // Act
        reviewService.createReview(taskId, taskerId, new CreateReviewRequest(3, "Kasnio je"));

        // Assert - prosjek klijenta postoji, ali se racuna na zahtjev
        assertThat(reviewRepository.findAverageRatingByReviewee(clientId)).contains(3.0);
        assertThat(taskerProfileRepository.findByUser(user(clientId))).isEmpty();
        assertThat(cachedAverage()).isNull();
    }

    @Test
    @DisplayName("The reviewee is notified")
    void createReview_notifiesTheReviewee() {
        // Arrange
        UUID taskId = closedTask();

        // Act
        reviewService.createReview(taskId, clientId, new CreateReviewRequest(5, null));

        // Assert
        List<Notification> taskerNotifications =
                notificationRepository.findByRecipientOrderByCreatedAtDesc(user(taskerId));
        assertThat(taskerNotifications)
                .extracting(Notification::getType)
                .contains(NotificationType.REVIEW_RECEIVED);
    }

    @Test
    @DisplayName("Received reviews are returned newest first")
    void getReceivedReviews_returnsNewestFirst() {
        // Arrange
        UUID first = closedTask();
        UUID second = closedTask();
        reviewService.createReview(first, clientId, new CreateReviewRequest(5, "Prva"));
        reviewService.createReview(second, clientId, new CreateReviewRequest(3, "Druga"));

        // Act
        var page = reviewService.getReceivedReviews(taskerId, PageRequest.of(0, 20));

        // Assert
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).first()
                .extracting(r -> r.comment())
                .isEqualTo("Druga");
    }

    @Test
    @DisplayName("Two reviews of the same tasker at the same time still leave the average correct")
    void concurrentReviews_leaveTheCachedAverageConsistent() throws Exception {
        // Arrange - dva zatvorena posla istog taskera, dva razlicita recenzenta
        UUID firstTask = closedTask();
        UUID secondTask = closedTask();
        UUID otherClientId = register("review.client2@test.ba").userId();
        reassignClient(secondTask, otherClientId);

        // Act - bez @Transactional na testu, pa svaka nit ima svoju transakciju
        Queue<Throwable> caught = runInParallel(
                () -> reviewService.createReview(firstTask, clientId, new CreateReviewRequest(5, null)),
                () -> reviewService.createReview(secondTask, otherClientId, new CreateReviewRequest(3, null)));

        // Assert - obje ocjene su prosle; ovdje se nista ne takmici za isti red
        assertThat(caught).isEmpty();
        assertThat(reviewRepository.findAll()).hasSize(2);

        // Assert - i keširani prosjek je 4.00, a ne 5.00 ili 3.00. Bez loka na
        // profilu obje transakcije procitaju prosjek prije nego ijedna commita, pa
        // druga prepise prvu i ostane vrijednost jedne jedine ocjene.
        assertThat(cachedAverage()).isEqualByComparingTo("4.00");
    }

    // ---------- helpers ----------

    /** Vodi task od kreiranja do CLOSED, jedinog stanja u kojem se smije ocjenjivati. */
    private UUID closedTask() {
        UUID taskId = taskService.createTask(clientId, new CreateTaskRequest(
                "Popravka slavine", "Curi ispod sudopera",
                category.getId(), municipality.getId(), new BigDecimal("80.00"))).id();
        taskService.publishTask(taskId, clientId);

        UUID offerId = offerService.submitOffer(taskId, taskerId,
                new CreateOfferRequest(new BigDecimal("75.00"), "Mogu danas")).id();
        offerService.acceptOffer(offerId, clientId);

        taskService.startTask(taskId, taskerId);
        taskService.completeTask(taskId, taskerId);
        taskService.closeTask(taskId, clientId);

        return taskId;
    }

    /**
     * Prebacuje posao na drugog klijenta. Potrebno samo za test utrkivanja: dvije
     * ocjene istom taskeru moraju doci od razlicitih recenzenata da se ne sudaraju
     * na jedinstvenosti umjesto na prosjeku.
     */
    private void reassignClient(UUID taskId, UUID newClientId) {
        transactionTemplate.executeWithoutResult(status ->
                taskRepository.findById(taskId).orElseThrow().setClient(user(newClientId)));
    }

    private Queue<Throwable> runInParallel(Runnable first, Runnable second) throws Exception {
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(2);
        Queue<Throwable> caught = new ConcurrentLinkedQueue<>();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        for (Runnable action : List.of(first, second)) {
            pool.submit(() -> {
                try {
                    startSignal.await();
                    action.run();
                } catch (Throwable t) {
                    caught.add(t);
                } finally {
                    finished.countDown();
                }
            });
        }

        startSignal.countDown();
        assertThat(finished.await(15, TimeUnit.SECONDS)).as("nitima je isteklo vrijeme").isTrue();
        pool.shutdown();

        return caught;
    }

    /**
     * Ocjena s profila taskera. Vraca null kad prosjek nije upisan - namjerno se
     * ne koristi Optional.map, jer bi prazan Optional nad null vrijednoscu bio
     * nerazluciv od nepostojeceg profila.
     */
    private BigDecimal cachedAverage() {
        TaskerProfile profile = taskerProfileRepository.findByUser(user(taskerId)).orElseThrow();
        return profile.getAverageRating();
    }

    private User user(UUID userId) {
        return userRepository.findById(userId).orElseThrow();
    }

    private AuthResponse register(String email) {
        return authService.register(new RegisterRequest(email, "password123", "Test", "User", null));
    }
}
