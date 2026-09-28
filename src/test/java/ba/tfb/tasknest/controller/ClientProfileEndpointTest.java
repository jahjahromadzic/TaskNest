package ba.tfb.tasknest.controller;

import ba.tfb.tasknest.AbstractIntegrationTest;
import ba.tfb.tasknest.dto.auth.AuthResponse;
import ba.tfb.tasknest.dto.auth.RegisterRequest;
import ba.tfb.tasknest.dto.offer.CreateOfferRequest;
import ba.tfb.tasknest.dto.review.CreateReviewRequest;
import ba.tfb.tasknest.dto.task.CreateTaskRequest;
import ba.tfb.tasknest.entity.Category;
import ba.tfb.tasknest.entity.Municipality;
import ba.tfb.tasknest.repository.CategoryRepository;
import ba.tfb.tasknest.repository.MunicipalityRepository;
import ba.tfb.tasknest.service.AuthService;
import ba.tfb.tasknest.service.OfferService;
import ba.tfb.tasknest.service.ReviewService;
import ba.tfb.tasknest.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ClientProfileEndpointTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthService authService;
    @Autowired private TaskService taskService;
    @Autowired private OfferService offerService;
    @Autowired private ReviewService reviewService;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private MunicipalityRepository municipalityRepository;

    private Category category;
    private Municipality municipality;
    private AuthResponse client;
    private AuthResponse tasker;
    private AuthResponse otherClient;

    @BeforeEach
    void setUp() {
        category = categoryRepository.findAll().getFirst();
        municipality = municipalityRepository.findAll().getFirst();

        client = register("profile.client@test.ba", "Amra", "Hodžić");
        tasker = register("profile.tasker@test.ba", "Selma", "Karić");
        otherClient = register("profile.other@test.ba", "Haris", "Mehić");
        authService.activateTaskerRole(tasker.userId());
    }

    @Test
    @DisplayName("The client profile counts posted tasks, hires, finished and cancelled jobs, and only reviews received as a client")
    void profile_summarizesTheClientsHistory() throws Exception {
        // Arrange
        taskService.createTask(client.userId(), taskRequest("Nacrt"));
        publishedTask(client, "Otvoren oglas");
        UUID cancelled = publishedTask(client, "Otkazan oglas");
        taskService.cancelTask(cancelled, client.userId());
        UUID closed = hire(client, tasker, "Krečenje kuhinje");
        finish(closed, client, tasker);
        hire(client, tasker, "Montaža ormara");
        reviewService.createReview(closed, tasker.userId(), new CreateReviewRequest(4, "Fair and on time"));

        authService.activateTaskerRole(client.userId());
        UUID jobAsTasker = hire(otherClient, client, "Selidba");
        finish(jobAsTasker, otherClient, client);
        reviewService.createReview(jobAsTasker, otherClient.userId(), new CreateReviewRequest(1, "Came late"));

        // Act & Assert
        mockMvc.perform(get("/api/users/{userId}/client-profile", client.userId())
                        .header("Authorization", bearer(tasker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(client.userId().toString()))
                .andExpect(jsonPath("$.fullName").value("Amra Hodžić"))
                .andExpect(jsonPath("$.memberSince").isNotEmpty())
                .andExpect(jsonPath("$.averageRating").value(4.0))
                .andExpect(jsonPath("$.reviewCount").value(1))
                .andExpect(jsonPath("$.postedTasksCount").value(4))
                .andExpect(jsonPath("$.hiresCount").value(2))
                .andExpect(jsonPath("$.completedJobsCount").value(1))
                .andExpect(jsonPath("$.cancelledTasksCount").value(1))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.phone").doesNotExist());
    }

    @Test
    @DisplayName("A client with no activity has zero counts and no rating")
    void profile_isEmpty_forANewClient() throws Exception {
        mockMvc.perform(get("/api/users/{userId}/client-profile", client.userId())
                        .header("Authorization", bearer(tasker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").value(nullValue()))
                .andExpect(jsonPath("$.reviewCount").value(0))
                .andExpect(jsonPath("$.postedTasksCount").value(0))
                .andExpect(jsonPath("$.hiresCount").value(0));
    }

    @Test
    @DisplayName("Past hires list the tasker of every hired task, newest assignment first")
    void hires_listNewestAssignmentFirst() throws Exception {
        // Arrange
        publishedTask(client, "Bez ponude");
        UUID first = hire(client, tasker, "Krečenje kuhinje");
        finish(first, client, tasker);
        hire(client, tasker, "Montaža ormara");

        // Act & Assert
        mockMvc.perform(get("/api/users/{userId}/hires", client.userId())
                        .header("Authorization", bearer(tasker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].title").value("Montaža ormara"))
                .andExpect(jsonPath("$.content[0].status").value("ASSIGNED"))
                .andExpect(jsonPath("$.content[0].taskerId").value(tasker.userId().toString()))
                .andExpect(jsonPath("$.content[0].taskerName").value("Selma Karić"))
                .andExpect(jsonPath("$.content[0].categorySlug").value(category.getSlug()))
                .andExpect(jsonPath("$.content[1].title").value("Krečenje kuhinje"))
                .andExpect(jsonPath("$.content[1].status").value("CLOSED"))
                .andExpect(jsonPath("$.content[1].completedAt").isNotEmpty());
    }

    @Test
    @DisplayName("Received reviews can be narrowed to the ones given as a client or as a tasker")
    void reviews_canBeFilteredBySide() throws Exception {
        // Arrange
        authService.activateTaskerRole(client.userId());
        UUID asClient = hire(client, tasker, "Krečenje kuhinje");
        finish(asClient, client, tasker);
        reviewService.createReview(asClient, tasker.userId(), new CreateReviewRequest(5, "Great client"));
        UUID asTasker = hire(otherClient, client, "Selidba");
        finish(asTasker, otherClient, client);
        reviewService.createReview(asTasker, otherClient.userId(), new CreateReviewRequest(2, "Came late"));

        // Act & Assert
        mockMvc.perform(get("/api/users/{userId}/reviews", client.userId()).param("as", "CLIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].comment").value("Great client"));
        mockMvc.perform(get("/api/users/{userId}/reviews", client.userId()).param("as", "TASKER"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].comment").value("Came late"));
        mockMvc.perform(get("/api/users/{userId}/reviews", client.userId()))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("The client profile requires a signed-in user and answers 404 for an unknown user")
    void profile_requiresAuthentication_andKnownUser() throws Exception {
        mockMvc.perform(get("/api/users/{userId}/client-profile", client.userId()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users/{userId}/hires", client.userId()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users/{userId}/client-profile", UUID.randomUUID())
                        .header("Authorization", bearer(tasker)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/users/{userId}/hires", UUID.randomUUID())
                        .header("Authorization", bearer(tasker)))
                .andExpect(status().isNotFound());
    }

    private UUID publishedTask(AuthResponse owner, String title) {
        UUID taskId = taskService.createTask(owner.userId(), taskRequest(title)).id();
        taskService.publishTask(taskId, owner.userId());
        return taskId;
    }

    private UUID hire(AuthResponse owner, AuthResponse worker, String title) {
        UUID taskId = publishedTask(owner, title);
        UUID offerId = offerService.submitOffer(taskId, worker.userId(),
                new CreateOfferRequest(new BigDecimal("75.00"), "Mogu danas")).id();
        offerService.acceptOffer(offerId, owner.userId());
        return taskId;
    }

    private void finish(UUID taskId, AuthResponse owner, AuthResponse worker) {
        taskService.startTask(taskId, worker.userId());
        taskService.completeTask(taskId, worker.userId());
        taskService.closeTask(taskId, owner.userId());
    }

    private CreateTaskRequest taskRequest(String title) {
        return new CreateTaskRequest(title, "Opis posla za test",
                category.getId(), municipality.getId(), new BigDecimal("80.00"));
    }

    private AuthResponse register(String email, String firstName, String lastName) {
        return authService.register(new RegisterRequest(email, "password123", firstName, lastName, null));
    }

    private static String bearer(AuthResponse auth) {
        return "Bearer " + auth.token();
    }
}
