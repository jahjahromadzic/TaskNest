package ba.tfb.tasknest.service;

import ba.tfb.tasknest.AbstractIntegrationTest;
import ba.tfb.tasknest.dto.auth.LoginRequest;
import ba.tfb.tasknest.dto.auth.RegisterRequest;
import ba.tfb.tasknest.dto.offer.CreateOfferRequest;
import ba.tfb.tasknest.dto.offer.TaskOfferResponse;
import ba.tfb.tasknest.dto.review.CreateReviewRequest;
import ba.tfb.tasknest.dto.task.CreateTaskRequest;
import ba.tfb.tasknest.entity.Category;
import ba.tfb.tasknest.entity.Municipality;
import ba.tfb.tasknest.entity.Notification;
import ba.tfb.tasknest.entity.enums.NotificationType;
import ba.tfb.tasknest.entity.enums.OfferStatus;
import ba.tfb.tasknest.repository.CategoryRepository;
import ba.tfb.tasknest.repository.MunicipalityRepository;
import ba.tfb.tasknest.repository.NotificationRepository;
import ba.tfb.tasknest.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TaskOffersIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthService authService;
    @Autowired private TaskService taskService;
    @Autowired private OfferService offerService;
    @Autowired private ReviewService reviewService;

    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private MunicipalityRepository municipalityRepository;
    @Autowired private NotificationRepository notificationRepository;

    private Category category;
    private Municipality municipality;
    private UUID clientId;
    private UUID experiencedTaskerId;
    private UUID newTaskerId;

    @BeforeEach
    void setUp() {
        category = categoryRepository.findAll().getFirst();
        municipality = municipalityRepository.findAll().getFirst();

        clientId = register("offers.client@test.ba", "Amra", "Client");
        experiencedTaskerId = register("offers.emir@test.ba", "Emir", "Tasker");
        newTaskerId = register("offers.tarik@test.ba", "Tarik", "Tasker");
        authService.activateTaskerRole(experiencedTaskerId);
        authService.activateTaskerRole(newTaskerId);
    }

    @Test
    @DisplayName("The owner sees every offer together with the tasker's reputation")
    void getOffersForTask_includesTheTaskersReputation() {
        // Arrange
        finishAndReviewAJob(experiencedTaskerId, 5);
        UUID taskId = publishedTask();
        offerService.submitOffer(taskId, experiencedTaskerId, new CreateOfferRequest(new BigDecimal("70"), "Today"));
        offerService.submitOffer(taskId, newTaskerId, new CreateOfferRequest(new BigDecimal("55"), "Tomorrow"));

        // Act
        List<TaskOfferResponse> offers = offerService.getOffersForTask(taskId, clientId);

        // Assert
        assertThat(offers).hasSize(2);

        TaskOfferResponse experienced = offers.getFirst();
        assertThat(experienced.taskerName()).isEqualTo("Emir Tasker");
        assertThat(experienced.price()).isEqualByComparingTo("70");
        assertThat(experienced.status()).isEqualTo(OfferStatus.PENDING);
        assertThat(experienced.taskerRating()).isEqualByComparingTo("5.00");
        assertThat(experienced.taskerCompletedJobs()).isEqualTo(1);
        assertThat(experienced.taskerReviewCount()).isEqualTo(1);
        assertThat(experienced.taskerVerified()).isFalse();

        TaskOfferResponse newcomer = offers.get(1);
        assertThat(newcomer.taskerName()).isEqualTo("Tarik Tasker");
        assertThat(newcomer.taskerRating()).isNull();
        assertThat(newcomer.taskerCompletedJobs()).isZero();
        assertThat(newcomer.taskerReviewCount()).isZero();
    }

    @Test
    @DisplayName("Accepting one offer shows it as accepted and the others as rejected")
    void getOffersForTask_reflectsTheAcceptedOffer() {
        // Arrange
        UUID taskId = publishedTask();
        UUID chosen = offerService.submitOffer(taskId, experiencedTaskerId,
                new CreateOfferRequest(new BigDecimal("70"), null)).id();
        offerService.submitOffer(taskId, newTaskerId, new CreateOfferRequest(new BigDecimal("55"), null));

        // Act
        offerService.acceptOffer(chosen, clientId);
        List<TaskOfferResponse> offers = offerService.getOffersForTask(taskId, clientId);

        // Assert
        assertThat(offers).extracting(TaskOfferResponse::status)
                .containsExactly(OfferStatus.ACCEPTED, OfferStatus.REJECTED);
    }

    @Test
    @DisplayName("A tasker can look up their own offer on a task, and gets nothing before they send one")
    void myOfferForTask_isEmptyUntilTheTaskerOffers() throws Exception {
        // Arrange
        UUID taskId = publishedTask();
        String taskerToken = token("offers.emir@test.ba");

        // Act + Assert
        mockMvc.perform(get("/api/tasks/" + taskId + "/offers/mine").header("Authorization", "Bearer " + taskerToken))
                .andExpect(status().isNoContent());

        offerService.submitOffer(taskId, experiencedTaskerId, new CreateOfferRequest(new BigDecimal("65"), "Tomorrow"));

        mockMvc.perform(get("/api/tasks/" + taskId + "/offers/mine").header("Authorization", "Bearer " + taskerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(65))
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(get("/api/tasks/" + taskId + "/offers/mine")
                        .header("Authorization", "Bearer " + token("offers.client@test.ba")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A tasker profile can be opened from the user id that an offer carries")
    void profileOfUser_isFoundByUserId_andMissingForPlainClients() throws Exception {
        // Arrange
        String clientToken = token("offers.client@test.ba");

        // Act + Assert
        mockMvc.perform(get("/api/tasker-profiles/users/" + experiencedTaskerId)
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(experiencedTaskerId.toString()))
                .andExpect(jsonPath("$.fullName").value("Emir Tasker"));

        mockMvc.perform(get("/api/tasker-profiles/users/" + clientId)
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("The client hears about every new offer and the tasker hears when they are hired")
    void offers_notifyTheClientAndTheHiredTasker() {
        // Arrange
        UUID taskId = publishedTask();
        UUID offerId = offerService.submitOffer(taskId, newTaskerId,
                new CreateOfferRequest(new BigDecimal("55.50"), null)).id();

        // Act
        offerService.acceptOffer(offerId, clientId);

        // Assert
        assertThat(notificationsOf(clientId))
                .extracting(Notification::getType, Notification::getContent, Notification::getRelatedEntityId)
                .containsExactly(tuple(NotificationType.NEW_OFFER, "Tarik Tasker offered 55.5 KM for: Fix the tap", taskId));
        assertThat(notificationsOf(newTaskerId))
                .extracting(Notification::getType, Notification::getContent, Notification::getRelatedEntityId)
                .containsExactly(tuple(NotificationType.OFFER_ACCEPTED, "You were hired for: Fix the tap", taskId));
    }

    private List<Notification> notificationsOf(UUID userId) {
        return notificationRepository.findByRecipientOrderByCreatedAtDesc(userRepository.getReferenceById(userId));
    }

    private String token(String email) {
        return authService.login(new LoginRequest(email, "password123")).token();
    }

    private void finishAndReviewAJob(UUID taskerId, int rating) {
        UUID taskId = publishedTask();
        UUID offerId = offerService.submitOffer(taskId, taskerId,
                new CreateOfferRequest(new BigDecimal("80"), null)).id();
        offerService.acceptOffer(offerId, clientId);
        taskService.startTask(taskId, taskerId);
        taskService.completeTask(taskId, taskerId);
        taskService.closeTask(taskId, clientId);
        reviewService.createReview(taskId, clientId, new CreateReviewRequest(rating, "Great work"));
    }

    private UUID publishedTask() {
        UUID taskId = taskService.createTask(clientId, new CreateTaskRequest(
                "Fix the tap", "It drips", category.getId(), municipality.getId(),
                "Zmaja od Bosne 12", new BigDecimal("80"))).id();
        taskService.publishTask(taskId, clientId);
        return taskId;
    }

    private UUID register(String email, String firstName, String lastName) {
        return authService.register(new RegisterRequest(email, "password123", firstName, lastName, null)).userId();
    }
}
