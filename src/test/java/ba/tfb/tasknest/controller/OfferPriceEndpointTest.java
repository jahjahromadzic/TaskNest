package ba.tfb.tasknest.controller;

import ba.tfb.tasknest.AbstractIntegrationTest;
import ba.tfb.tasknest.dto.auth.AuthResponse;
import ba.tfb.tasknest.dto.auth.LoginRequest;
import ba.tfb.tasknest.dto.auth.RegisterRequest;
import ba.tfb.tasknest.dto.notification.NotificationResponse;
import ba.tfb.tasknest.dto.offer.CreateOfferRequest;
import ba.tfb.tasknest.dto.task.CreateTaskRequest;
import ba.tfb.tasknest.entity.Category;
import ba.tfb.tasknest.entity.Offer;
import ba.tfb.tasknest.entity.enums.NotificationType;
import ba.tfb.tasknest.entity.enums.OfferStatus;
import ba.tfb.tasknest.repository.CategoryRepository;
import ba.tfb.tasknest.repository.MunicipalityRepository;
import ba.tfb.tasknest.repository.OfferRepository;
import ba.tfb.tasknest.service.AuthService;
import ba.tfb.tasknest.service.NotificationService;
import ba.tfb.tasknest.service.OfferService;
import ba.tfb.tasknest.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class OfferPriceEndpointTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthService authService;
    @Autowired private TaskService taskService;
    @Autowired private OfferService offerService;
    @Autowired private OfferRepository offerRepository;
    @Autowired private NotificationService notificationService;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private MunicipalityRepository municipalityRepository;

    private AuthResponse client;
    private AuthResponse tasker;
    private UUID taskId;
    private UUID offerId;

    @BeforeEach
    void setUp() {
        client = register("price.client@test.ba", "Amra", "Hodžić");
        tasker = register("price.tasker@test.ba", "Emir", "Kovačević");
        authService.activateTaskerRole(tasker.userId());
        tasker = authService.login(new LoginRequest("price.tasker@test.ba", "password123"));
        Category category = categoryRepository.findAll().stream().filter(Category::isActive).findFirst().orElseThrow();
        taskId = taskService.createTask(client.userId(), new CreateTaskRequest("Curi bojler", null,
                category.getId(), municipalityRepository.findAll().getFirst().getId(),
                "Zmaja od Bosne 12", null, null, new BigDecimal("80"))).id();
        taskService.publishTask(taskId, client.userId());
        offerId = offerService.submitOffer(taskId, tasker.userId(), new CreateOfferRequest(new BigDecimal("70"), "Mogu danas")).id();
    }

    @Test
    @DisplayName("The tasker lowers the price of a pending offer and the client is told the old and the new price")
    void updatePrice_changesThePendingOfferAndTellsTheClient() throws Exception {
        updatePrice(tasker, "60")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(60))
                .andExpect(jsonPath("$.status").value("PENDING"));

        assertThat(offerRepository.findById(offerId).orElseThrow().getPrice()).isEqualByComparingTo("60");
        assertThat(updates()).singleElement()
                .satisfies(notification -> {
                    assertThat(notification.relatedEntityId()).isEqualTo(taskId);
                    assertThat(notification.content()).isEqualTo("Emir Kovačević changed the offer from 70 KM to 60 KM for: Curi bojler");
                });
    }

    @Test
    @DisplayName("Saving the same price sends no notification")
    void updatePrice_withTheSamePrice_isQuiet() throws Exception {
        updatePrice(tasker, "70.00").andExpect(status().isOk());

        assertThat(updates()).isEmpty();
    }

    @Test
    @DisplayName("Only the tasker who made the offer can change it, and the price must be above zero")
    void updatePrice_isOnlyForTheOwnerWithAValidPrice() throws Exception {
        AuthResponse other = register("price.other@test.ba", "Selma", "Karić");
        authService.activateTaskerRole(other.userId());
        AuthResponse otherWithRole = authService.login(new LoginRequest("price.other@test.ba", "password123"));

        updatePrice(otherWithRole, "10").andExpect(status().isForbidden());
        updatePrice(client, "10").andExpect(status().isForbidden());
        updatePrice(tasker, "0").andExpect(status().isBadRequest());
        assertThat(offerRepository.findById(offerId).orElseThrow().getPrice()).isEqualByComparingTo("70");
    }

    @Test
    @DisplayName("Once the client accepts, the price is locked")
    void updatePrice_isRefusedAfterAcceptance() throws Exception {
        accept("70").andExpect(status().isOk());

        updatePrice(tasker, "120")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Only a pending offer can be changed"));
        assertThat(offerRepository.findById(offerId).orElseThrow().getPrice()).isEqualByComparingTo("70");
    }

    @Test
    @DisplayName("A client who still sees the old price cannot accept the new one by accident")
    void accept_refusesAStalePrice() throws Exception {
        updatePrice(tasker, "90").andExpect(status().isOk());

        accept("70")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The tasker changed the price to 90 KM. Check the new price before accepting."));
        assertThat(offerRepository.findById(offerId).orElseThrow().getStatus()).isEqualTo(OfferStatus.PENDING);

        accept("90")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(90))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @RepeatedTest(3)
    @DisplayName("A price change and an acceptance at the same moment never hire at a price the client did not see")
    void concurrentChangeAndAccept_neverHireAtAnUnseenPrice() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Callable<Integer>> calls = List.of(
                () -> updatePrice(tasker, "150").andReturn().getResponse().getStatus(),
                () -> accept("70").andReturn().getResponse().getStatus());

        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> future : pool.invokeAll(calls)) {
            statuses.add(future.get());
        }
        pool.shutdown();

        Offer offer = offerRepository.findById(offerId).orElseThrow();
        if (offer.getStatus() == OfferStatus.ACCEPTED) {
            assertThat(offer.getPrice()).isEqualByComparingTo("70");
        } else {
            assertThat(offer.getPrice()).isEqualByComparingTo("150");
            assertThat(statuses.get(1)).isEqualTo(409);
        }
        assertThat(statuses).filteredOn(code -> code == 200).hasSize(1);
    }

    private List<NotificationResponse> updates() {
        return notificationService.getMyNotifications(client.userId(), PageRequest.of(0, 20)).getContent().stream()
                .filter(notification -> notification.type() == NotificationType.OFFER_UPDATED)
                .toList();
    }

    private ResultActions updatePrice(AuthResponse who, String price) throws Exception {
        return mockMvc.perform(put("/api/offers/" + offerId).header("Authorization", "Bearer " + who.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"price": %s}
                        """.formatted(price)));
    }

    private ResultActions accept(String expectedPrice) throws Exception {
        return mockMvc.perform(post("/api/offers/" + offerId + "/accept").header("Authorization", "Bearer " + client.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"expectedPrice": %s}
                        """.formatted(expectedPrice)));
    }

    private AuthResponse register(String email, String firstName, String lastName) {
        return authService.register(new RegisterRequest(email, "password123", firstName, lastName, null));
    }
}
