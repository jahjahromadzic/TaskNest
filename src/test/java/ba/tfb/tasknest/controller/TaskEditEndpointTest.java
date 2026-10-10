package ba.tfb.tasknest.controller;

import ba.tfb.tasknest.AbstractIntegrationTest;
import ba.tfb.tasknest.dto.auth.AuthResponse;
import ba.tfb.tasknest.dto.auth.RegisterRequest;
import ba.tfb.tasknest.dto.notification.NotificationResponse;
import ba.tfb.tasknest.dto.offer.CreateOfferRequest;
import ba.tfb.tasknest.dto.task.CreateTaskRequest;
import ba.tfb.tasknest.entity.Category;
import ba.tfb.tasknest.entity.Municipality;
import ba.tfb.tasknest.entity.enums.NotificationType;
import ba.tfb.tasknest.repository.CategoryRepository;
import ba.tfb.tasknest.repository.MunicipalityRepository;
import ba.tfb.tasknest.service.AuthService;
import ba.tfb.tasknest.service.NotificationService;
import ba.tfb.tasknest.service.OfferService;
import ba.tfb.tasknest.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TaskEditEndpointTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthService authService;
    @Autowired private TaskService taskService;
    @Autowired private OfferService offerService;
    @Autowired private NotificationService notificationService;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private MunicipalityRepository municipalityRepository;

    private AuthResponse client;
    private Category category;
    private Category otherCategory;
    private Municipality municipality;
    private Municipality otherMunicipality;

    @BeforeEach
    void setUp() {
        client = register("edit.client@test.ba");
        List<Category> categories = categoryRepository.findAll().stream().filter(Category::isActive).toList();
        category = categories.get(0);
        otherCategory = categories.get(1);
        List<Municipality> municipalities = municipalityRepository.findAll();
        municipality = municipalities.get(0);
        otherMunicipality = municipalities.get(1);
    }

    @Test
    @DisplayName("The owner changes every field of an open task and the taskers waiting on an offer are told")
    void edit_changesAnOpenTask_andTellsTaskersWithPendingOffers() throws Exception {
        // Arrange
        UUID taskId = publishedTask();
        AuthResponse waiting = tasker("edit.waiting@test.ba");
        AuthResponse withdrawn = tasker("edit.withdrawn@test.ba");
        offerService.submitOffer(taskId, waiting.userId(), new CreateOfferRequest(new BigDecimal("60"), "Mogu sutra"));
        UUID withdrawnOffer = offerService.submitOffer(taskId, withdrawn.userId(),
                new CreateOfferRequest(new BigDecimal("55"), "Mogu danas")).id();
        offerService.withdrawOffer(withdrawnOffer, withdrawn.userId());

        // Act
        edit(taskId, client, """
                {"title": "Popravka česme i bojlera", "description": "Česma curi, bojler ne grije.",
                 "categoryId": "%s", "municipalityId": "%s", "address": "Zmaja od Bosne 12", "budget": 120}
                """.formatted(otherCategory.getId(), otherMunicipality.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Popravka česme i bojlera"))
                .andExpect(jsonPath("$.description").value("Česma curi, bojler ne grije."))
                .andExpect(jsonPath("$.categoryId").value(otherCategory.getId().toString()))
                .andExpect(jsonPath("$.municipalityId").value(otherMunicipality.getId().toString()))
                .andExpect(jsonPath("$.budget").value(120))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        // Assert
        assertThat(updates(waiting)).singleElement()
                .satisfies(notification -> {
                    assertThat(notification.relatedEntityId()).isEqualTo(taskId);
                    assertThat(notification.content()).contains("Popravka česme i bojlera");
                });
        assertThat(updates(withdrawn)).isEmpty();
    }

    @Test
    @DisplayName("Saving a task without any change does not bother the taskers")
    void edit_withoutChanges_sendsNoNotification() throws Exception {
        // Arrange
        UUID taskId = publishedTask();
        AuthResponse waiting = tasker("edit.same@test.ba");
        offerService.submitOffer(taskId, waiting.userId(), new CreateOfferRequest(new BigDecimal("60"), "Mogu"));

        // Act
        edit(taskId, client, """
                {"title": "Popravka česme", "description": "Curi česma u kuhinji",
                 "categoryId": "%s", "municipalityId": "%s", "address": "Zmaja od Bosne 12", "budget": 50.00}
                """.formatted(category.getId(), municipality.getId()))
                .andExpect(status().isOk());

        // Assert
        assertThat(updates(waiting)).isEmpty();
    }

    @Test
    @DisplayName("Sending back the pin the task already has is not a change either")
    void edit_withTheSamePin_sendsNoNotification() throws Exception {
        // Arrange
        UUID taskId = publishedTask();
        AuthResponse waiting = tasker("edit.samepin@test.ba");
        offerService.submitOffer(taskId, waiting.userId(), new CreateOfferRequest(new BigDecimal("60"), "Mogu"));

        // Act
        edit(taskId, client, """
                {"title": "Popravka česme", "description": "Curi česma u kuhinji",
                 "categoryId": "%s", "municipalityId": "%s", "address": "Zmaja od Bosne 12",
                 "latitude": 43.8564300, "longitude": 18.4130290, "budget": 50.00}
                """.formatted(category.getId(), municipality.getId()))
                .andExpect(status().isOk());

        // Assert
        assertThat(updates(waiting)).isEmpty();
    }

    @Test
    @DisplayName("A draft can be edited and stays a draft")
    void edit_keepsADraftADraft() throws Exception {
        // Arrange
        UUID taskId = draft();

        // Act & Assert
        edit(taskId, client, body("Nova verzija nacrta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Nova verzija nacrta"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    @DisplayName("Once a tasker is hired the task can no longer be edited")
    void edit_isRefused_afterAnOfferWasAccepted() throws Exception {
        // Arrange
        UUID taskId = publishedTask();
        AuthResponse hired = tasker("edit.hired@test.ba");
        UUID offerId = offerService.submitOffer(taskId, hired.userId(),
                new CreateOfferRequest(new BigDecimal("60"), "Mogu")).id();
        offerService.acceptOffer(offerId, client.userId());

        // Act & Assert
        edit(taskId, client, body("Kasna izmjena"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Only a draft or a task that is open for offers can be edited"));
        assertThat(taskService.getTask(taskId, client.userId()).title()).isEqualTo("Popravka česme");
    }

    @Test
    @DisplayName("Another client cannot edit someone else's task")
    void edit_isForbidden_forAnotherClient() throws Exception {
        // Arrange
        UUID taskId = publishedTask();
        AuthResponse stranger = register("edit.stranger@test.ba");

        // Act & Assert
        edit(taskId, stranger, body("Tuđa izmjena")).andExpect(status().isForbidden());
        assertThat(taskService.getTask(taskId, client.userId()).title()).isEqualTo("Popravka česme");
    }

    @Test
    @DisplayName("An empty title is refused with a field error")
    void edit_refusesAnEmptyTitle() throws Exception {
        // Arrange
        UUID taskId = publishedTask();

        // Act & Assert
        edit(taskId, client, body(" "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("title")));
    }

    private String body(String title) {
        return """
                {"title": "%s", "categoryId": "%s", "municipalityId": "%s", "address": "Zmaja od Bosne 12"}
                """.formatted(title, category.getId(), municipality.getId());
    }

    private List<NotificationResponse> updates(AuthResponse who) {
        return notificationService.getMyNotifications(who.userId(), PageRequest.of(0, 20)).getContent().stream()
                .filter(notification -> notification.type() == NotificationType.TASK_UPDATED)
                .toList();
    }

    private ResultActions edit(UUID taskId, AuthResponse who, String json) throws Exception {
        return mockMvc.perform(put("/api/tasks/" + taskId)
                .header("Authorization", "Bearer " + who.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private UUID draft() {
        return taskService.createTask(client.userId(), new CreateTaskRequest("Popravka česme", "Curi česma u kuhinji",
                category.getId(), municipality.getId(), "Zmaja od Bosne 12", null, null, new BigDecimal("50"))).id();
    }

    private UUID publishedTask() {
        UUID taskId = draft();
        taskService.publishTask(taskId, client.userId());
        return taskId;
    }

    private AuthResponse tasker(String email) {
        AuthResponse tasker = register(email);
        authService.activateTaskerRole(tasker.userId());
        return tasker;
    }

    private AuthResponse register(String email) {
        return authService.register(new RegisterRequest(email, "password123", "Test", "Korisnik", null));
    }
}
