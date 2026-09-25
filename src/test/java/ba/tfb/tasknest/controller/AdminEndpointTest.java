package ba.tfb.tasknest.controller;

import ba.tfb.tasknest.AbstractIntegrationTest;
import ba.tfb.tasknest.bootstrap.AdminBootstrap;
import ba.tfb.tasknest.dto.auth.AuthResponse;
import ba.tfb.tasknest.dto.auth.RegisterRequest;
import ba.tfb.tasknest.dto.offer.CreateOfferRequest;
import ba.tfb.tasknest.dto.task.CreateTaskRequest;
import ba.tfb.tasknest.entity.Category;
import ba.tfb.tasknest.entity.Conversation;
import ba.tfb.tasknest.entity.Municipality;
import ba.tfb.tasknest.entity.enums.AccountStatus;
import ba.tfb.tasknest.entity.enums.ConversationStatus;
import ba.tfb.tasknest.entity.enums.NotificationType;
import ba.tfb.tasknest.entity.enums.OfferStatus;
import ba.tfb.tasknest.entity.enums.RoleName;
import ba.tfb.tasknest.entity.enums.TaskStatus;
import ba.tfb.tasknest.exception.BusinessRuleException;
import ba.tfb.tasknest.repository.*;
import ba.tfb.tasknest.service.AuthService;
import ba.tfb.tasknest.service.OfferService;
import ba.tfb.tasknest.service.TaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Administracija kroz pravi HTTP lanac.
 * <p>
 * Ovdje je rola cijela provjera - nema vlasnistva koje bi servis dodatno
 * provjerio - pa se @PreAuthorize mora vidjeti kroz filter, a ne preskociti
 * direktnim pozivom servisa.
 */
@AutoConfigureMockMvc
class AdminEndpointTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthService authService;
    @Autowired private TaskService taskService;
    @Autowired private OfferService offerService;
    @Autowired private TransactionTemplate transactionTemplate;

    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private OfferRepository offerRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private MunicipalityRepository municipalityRepository;
    @Autowired private TaskerProfileRepository taskerProfileRepository;
    @Autowired private ConversationRepository conversationRepository;
    @Autowired private MessageRepository messageRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private NotificationRepository notificationRepository;

    private Category category;
    private Municipality municipality;
    private AuthResponse admin;
    private AuthResponse client;
    private AuthResponse tasker;

    @BeforeEach
    void setUp() {
        category = categoryRepository.findAll().getFirst();
        municipality = municipalityRepository.findAll().getFirst();

        admin = register("admin@test.ba");
        client = register("mod.client@test.ba");
        tasker = register("mod.tasker@test.ba");
        authService.activateTaskerRole(tasker.userId());

        // Admin nastaje tacno onako kako nastaje u produkciji: registracija, pa
        // unapredjenje preko konfigurisanog emaila.
        promoteToAdmin("admin@test.ba");
    }

    @AfterEach
    void tearDown() {
        messageRepository.deleteAll();
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

    @Nested
    class Access {

        @Test
        @DisplayName("A non-admin gets 403 on every admin endpoint")
        void nonAdmin_isForbidden() throws Exception {
            mockMvc.perform(get("/api/admin/users").header("Authorization", bearer(client)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/admin/users/{id}/suspend", tasker.userId())
                            .header("Authorization", bearer(client)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("An anonymous caller gets 401")
        void anonymous_isUnauthorized() throws Exception {
            mockMvc.perform(get("/api/admin/users"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("The configured admin can list users, with roles attached")
        void admin_canListUsers() throws Exception {
            mockMvc.perform(get("/api/admin/users")
                            .param("email", "MOD.TASKER")
                            .header("Authorization", bearer(admin)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].email").value("mod.tasker@test.ba"))
                    .andExpect(jsonPath("$.content[0].roles.length()").value(2));
        }

        @Test
        @DisplayName("Promotion is idempotent and ignores a missing account")
        void bootstrap_isIdempotent_andToleratesMissingAccount() {
            // Act - drugi restart s istim emailom, pa email bez naloga
            promoteToAdmin("admin@test.ba");
            promoteToAdmin("nepostoji@test.ba");

            // Assert - i dalje jedna ADMIN rola, bez izuzetka
            long adminRoles = transactionTemplate.execute(status ->
                    userRepository.findById(admin.userId()).orElseThrow().getRoles().stream()
                            .filter(role -> role.getName() == RoleName.ADMIN).count());
            assertThat(adminRoles).isEqualTo(1);
        }
    }

    @Nested
    class Suspension {

        @Test
        @DisplayName("A suspended user's existing token stops working on the very next request")
        void suspend_takesEffectImmediately() throws Exception {
            // Arrange - token radi prije suspenzije
            mockMvc.perform(get("/api/notifications").header("Authorization", bearer(tasker)))
                    .andExpect(status().isOk());

            // Act
            mockMvc.perform(post("/api/admin/users/{id}/suspend", tasker.userId())
                            .header("Authorization", bearer(admin)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accountStatus").value("SUSPENDED"));

            // Assert - isti, jos nevazeci-po-vremenu token vise ne prolazi
            mockMvc.perform(get("/api/notifications").header("Authorization", bearer(tasker)))
                    .andExpect(status().isUnauthorized());

            // Assert - ni refresh token ne moze izdati novi par
            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"" + tasker.refreshToken() + "\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("An admin cannot suspend themselves or another admin")
        void suspend_isRefused_forAdmins() throws Exception {
            AuthResponse secondAdmin = register("admin2@test.ba");
            promoteToAdmin("admin2@test.ba");

            mockMvc.perform(post("/api/admin/users/{id}/suspend", admin.userId())
                            .header("Authorization", bearer(admin)))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(post("/api/admin/users/{id}/suspend", secondAdmin.userId())
                            .header("Authorization", bearer(admin)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Suspension freezes offers: they stay pending but cannot be accepted")
        void suspend_freezesOffers_withoutCancellingThem() throws Exception {
            // Arrange
            UUID taskId = publishedTask();
            UUID offerId = submitOffer(taskId);

            // Act
            mockMvc.perform(post("/api/admin/users/{id}/suspend", tasker.userId())
                            .header("Authorization", bearer(admin)))
                    .andExpect(status().isOk());

            // Assert - ponuda i dalje postoji, reverzibilno
            assertThat(offerRepository.findById(offerId).orElseThrow().getStatus())
                    .isEqualTo(OfferStatus.PENDING);

            // Assert - ali se ne moze prihvatiti
            assertThatThrownBy(() -> offerService.acceptOffer(offerId, client.userId()))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("not active");
            assertThat(taskRepository.findById(taskId).orElseThrow().getStatus())
                    .isEqualTo(TaskStatus.PUBLISHED);
        }

        @Test
        @DisplayName("After reactivation the user can log in again")
        void reactivate_restoresAccess() throws Exception {
            mockMvc.perform(post("/api/admin/users/{id}/suspend", tasker.userId())
                    .header("Authorization", bearer(admin)));

            mockMvc.perform(post("/api/admin/users/{id}/reactivate", tasker.userId())
                            .header("Authorization", bearer(admin)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accountStatus").value("ACTIVE"));

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"mod.tasker@test.ba\",\"password\":\"password123\"}"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    class Moderation {

        @Test
        @DisplayName("Removing a task rejects its offers, archives conversations and tells the owner why")
        void removeTask_cleansUpAndNotifiesTheOwner() throws Exception {
            // Arrange
            UUID taskId = publishedTask();
            UUID offerId = submitOffer(taskId);

            // Act
            mockMvc.perform(post("/api/admin/tasks/{id}/remove", taskId)
                            .header("Authorization", bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"Zabranjen sadrzaj\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("REMOVED"));

            // Assert
            assertThat(offerRepository.findById(offerId).orElseThrow().getStatus())
                    .isEqualTo(OfferStatus.REJECTED);
            assertThat(conversationRepository.findAll())
                    .extracting(Conversation::getStatus)
                    .containsOnly(ConversationStatus.ARCHIVED);
            assertThat(notificationRepository.findAll())
                    .filteredOn(n -> n.getType() == NotificationType.TASK_REMOVED)
                    .singleElement()
                    .satisfies(n -> {
                        assertThat(n.getRecipient().getId()).isEqualTo(client.userId());
                        assertThat(n.getContent()).contains("Zabranjen sadrzaj");
                    });
        }

        @Test
        @DisplayName("Work already in progress cannot be removed")
        void removeTask_isRejected_whenWorkIsInProgress() throws Exception {
            // Arrange
            UUID taskId = publishedTask();
            offerService.acceptOffer(submitOffer(taskId), client.userId());
            taskService.startTask(taskId, tasker.userId());

            // Act + Assert - state machine ne dopusta IN_PROGRESS -> REMOVED
            mockMvc.perform(post("/api/admin/tasks/{id}/remove", taskId)
                            .header("Authorization", bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"Kasno\"}"))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("A removal without a reason is rejected")
        void removeTask_requiresAReason() throws Exception {
            UUID taskId = publishedTask();

            mockMvc.perform(post("/api/admin/tasks/{id}/remove", taskId)
                            .header("Authorization", bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"  \"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Verifying a tasker shows on their public profile")
        void verify_marksTheTaskerProfile() throws Exception {
            UUID profileId = transactionTemplate.execute(status -> taskerProfileRepository
                    .findByUser(userRepository.findById(tasker.userId()).orElseThrow())
                    .orElseThrow().getId());

            mockMvc.perform(post("/api/admin/tasker-profiles/{id}/verify", profileId)
                            .header("Authorization", bearer(admin)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.verified").value(true));

            mockMvc.perform(get("/api/tasker-profiles/{id}", profileId)
                            .header("Authorization", bearer(client)))
                    .andExpect(jsonPath("$.verified").value(true));
        }
    }

    // ---------- helpers ----------

    private void promoteToAdmin(String email) {
        AdminBootstrap bootstrap = new AdminBootstrap(userRepository, roleRepository, email);
        transactionTemplate.executeWithoutResult(status -> bootstrap.promoteConfiguredAdmin());
    }

    private UUID publishedTask() {
        UUID taskId = taskService.createTask(client.userId(), new CreateTaskRequest(
                "Popravka slavine", "Opis", category.getId(), municipality.getId(),
                new BigDecimal("80.00"))).id();
        taskService.publishTask(taskId, client.userId());
        return taskId;
    }

    private UUID submitOffer(UUID taskId) {
        return offerService.submitOffer(taskId, tasker.userId(),
                new CreateOfferRequest(new BigDecimal("75.00"), "Mogu")).id();
    }

    private AuthResponse register(String email) {
        return authService.register(new RegisterRequest(email, "password123", "Test", "User", null));
    }

    private static String bearer(AuthResponse auth) {
        return "Bearer " + auth.token();
    }
}
