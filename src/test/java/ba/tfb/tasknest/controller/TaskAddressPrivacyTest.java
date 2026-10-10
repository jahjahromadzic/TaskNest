package ba.tfb.tasknest.controller;

import ba.tfb.tasknest.AbstractIntegrationTest;
import ba.tfb.tasknest.dto.auth.AuthResponse;
import ba.tfb.tasknest.dto.auth.RegisterRequest;
import ba.tfb.tasknest.dto.offer.CreateOfferRequest;
import ba.tfb.tasknest.dto.task.CreateTaskRequest;
import ba.tfb.tasknest.entity.Category;
import ba.tfb.tasknest.repository.CategoryRepository;
import ba.tfb.tasknest.repository.MunicipalityRepository;
import ba.tfb.tasknest.service.AuthService;
import ba.tfb.tasknest.service.OfferService;
import ba.tfb.tasknest.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TaskAddressPrivacyTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthService authService;
    @Autowired private TaskService taskService;
    @Autowired private OfferService offerService;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private MunicipalityRepository municipalityRepository;

    private AuthResponse client;
    private AuthResponse hired;
    private AuthResponse other;
    private UUID taskId;
    private UUID hiredOffer;

    @BeforeEach
    void setUp() {
        client = register("privacy.client@test.ba");
        hired = tasker("privacy.hired@test.ba");
        other = tasker("privacy.other@test.ba");

        Category category = categoryRepository.findAll().stream().filter(Category::isActive).findFirst().orElseThrow();
        taskId = taskService.createTask(client.userId(), new CreateTaskRequest("Popravka slavine", "Curi",
                category.getId(), municipalityRepository.findAll().getFirst().getId(), "Zmaja od Bosne 12", null, null,
                new BigDecimal("50"))).id();
        taskService.publishTask(taskId, client.userId());

        hiredOffer = offerService.submitOffer(taskId, hired.userId(),
                new CreateOfferRequest(new BigDecimal("50"), "Mogu sutra")).id();
        offerService.submitOffer(taskId, other.userId(), new CreateOfferRequest(new BigDecimal("45"), "Mogu danas"));
    }

    @Test
    @DisplayName("A visitor who is not signed in sees the municipality but not the address")
    void guest_doesNotSeeTheAddress() throws Exception {
        expectHidden(mockMvc.perform(get("/api/tasks/" + taskId)))
                .andExpect(jsonPath("$.municipalityName").isNotEmpty());
    }

    @Test
    @DisplayName("The owner sees the address and its coordinates")
    void owner_seesTheAddress() throws Exception {
        expectShown(view(client));
    }

    @Test
    @DisplayName("Taskers who only sent an offer do not see the address")
    void taskerWithAnOffer_doesNotSeeTheAddress() throws Exception {
        expectHidden(view(hired));
        expectHidden(view(other));
    }

    @Test
    @DisplayName("Once hired, only the chosen tasker sees the address")
    void hiredTasker_seesTheAddress() throws Exception {
        offerService.acceptOffer(hiredOffer, client.userId());

        expectShown(view(hired));
        expectHidden(view(other));
    }

    @Test
    @DisplayName("A tasker who is no longer hired loses sight of the address")
    void releasedTasker_losesTheAddress() throws Exception {
        offerService.acceptOffer(hiredOffer, client.userId());
        taskService.reopenTask(taskId, client.userId());

        expectHidden(view(hired));
        expectShown(view(client));
    }

    @Test
    @DisplayName("The public task list never carries an address")
    void taskList_neverCarriesTheAddress() throws Exception {
        mockMvc.perform(get("/api/tasks").header("Authorization", "Bearer " + client.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(taskId.toString()))
                .andExpect(jsonPath("$.content[0].addressLine").doesNotExist())
                .andExpect(jsonPath("$.content[0].latitude").doesNotExist());
    }

    private ResultActions view(AuthResponse who) throws Exception {
        return mockMvc.perform(get("/api/tasks/" + taskId).header("Authorization", "Bearer " + who.token()));
    }

    private ResultActions expectShown(ResultActions result) throws Exception {
        return result.andExpect(status().isOk())
                .andExpect(jsonPath("$.addressLine").value("Zmaja od Bosne 12"))
                .andExpect(jsonPath("$.latitude").value(SARAJEVO.latitude().doubleValue()))
                .andExpect(jsonPath("$.longitude").value(SARAJEVO.longitude().doubleValue()));
    }

    private ResultActions expectHidden(ResultActions result) throws Exception {
        return result.andExpect(status().isOk())
                .andExpect(jsonPath("$.addressLine").value(nullValue()))
                .andExpect(jsonPath("$.latitude").value(nullValue()))
                .andExpect(jsonPath("$.longitude").value(nullValue()));
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
