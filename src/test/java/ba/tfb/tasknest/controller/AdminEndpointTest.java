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
import ba.tfb.tasknest.security.RefreshTokenCookie;
import ba.tfb.tasknest.service.AuthService;
import jakarta.servlet.http.Cookie;
import ba.tfb.tasknest.service.OfferService;
import ba.tfb.tasknest.service.TaskService;
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

        promoteToAdmin("admin@test.ba");
    }

    @Nested
    class Access {

        @Test
        @DisplayName("A non-admin gets 403 on every admin endpoint")
        void nonAdmin_isForbidden() throws Exception {
            mockMvc.perform(get("/api/admin/users").header("Authorization", bearer(client)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/admin/stats").header("Authorization", bearer(client)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/admin/tasks").header("Authorization", bearer(client)))
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
                            .param("search", "MOD.TASKER")
                            .header("Authorization", bearer(admin)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].email").value("mod.tasker@test.ba"))
                    .andExpect(jsonPath("$.content[0].roles.length()").value(2))
                    .andExpect(jsonPath("$.content[0].taskerProfileId").isNotEmpty())
                    .andExpect(jsonPath("$.content[0].taskerVerified").value(false));
        }

        @Test
        @DisplayName("Users can be found by name and narrowed to taskers")
        void admin_canSearchByNameAndRole() throws Exception {
            authService.register(new RegisterRequest("selma@test.ba", "password123", "Selma", "Karić", null));

            mockMvc.perform(get("/api/admin/users")
                            .param("search", "selma kar")
                            .header("Authorization", bearer(admin)))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].fullName").value("Selma Karić"))
                    .andExpect(jsonPath("$.content[0].taskerProfileId").doesNotExist());

            mockMvc.perform(get("/api/admin/users")
                            .param("role", "TASKER")
                            .header("Authorization", bearer(admin)))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].email").value("mod.tasker@test.ba"));
        }

        @Test
        @DisplayName("The overview counts users, taskers waiting for verification and tasks")
        void admin_seesTheOverview() throws Exception {
            publishedTask();
            UUID removed = publishedTask();
            taskService.removeTask(removed, "Spam");

            mockMvc.perform(post("/api/admin/users/{id}/suspend", client.userId())
                    .header("Authorization", bearer(admin)));

            mockMvc.perform(get("/api/admin/stats").header("Authorization", bearer(admin)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.users").value(3))
                    .andExpect(jsonPath("$.suspendedUsers").value(1))
                    .andExpect(jsonPath("$.taskers").value(1))
                    .andExpect(jsonPath("$.unverifiedTaskers").value(1))
                    .andExpect(jsonPath("$.openTasks").value(1))
                    .andExpect(jsonPath("$.removedTasks").value(1));
        }

        @Test
        @DisplayName("The task list for moderation hides drafts and filters by status and text")
        void admin_canListTasksForModeration() throws Exception {
            UUID open = publishedTask();
            UUID removed = publishedTask();
            taskService.removeTask(removed, "Spam");
            taskService.createTask(client.userId(), new CreateTaskRequest(
                    "Draft only", "Opis", category.getId(), municipality.getId(),
                    "Zmaja od Bosne 12", null, null, null));

            mockMvc.perform(get("/api/admin/tasks").header("Authorization", bearer(admin)))
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.content[0].clientEmail").value("mod.client@test.ba"))
                    .andExpect(jsonPath("$.content[0].categoryName").isNotEmpty());

            mockMvc.perform(get("/api/admin/tasks")
                            .param("status", "REMOVED")
                            .header("Authorization", bearer(admin)))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(removed.toString()));

            mockMvc.perform(get("/api/admin/tasks")
                            .param("status", "PUBLISHED")
                            .param("search", "SLAVINE")
                            .header("Authorization", bearer(admin)))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(open.toString()));

            mockMvc.perform(get("/api/admin/tasks")
                            .param("search", "nothing like this")
                            .header("Authorization", bearer(admin)))
                    .andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        @DisplayName("Promotion is idempotent and ignores a missing account")
        void bootstrap_isIdempotent_andToleratesMissingAccount() {
            // Act
            promoteToAdmin("admin@test.ba");
            promoteToAdmin("nepostoji@test.ba");

            // Assert
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
            // Arrange
            mockMvc.perform(get("/api/notifications").header("Authorization", bearer(tasker)))
                    .andExpect(status().isOk());

            // Act
            mockMvc.perform(post("/api/admin/users/{id}/suspend", tasker.userId())
                            .header("Authorization", bearer(admin)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accountStatus").value("SUSPENDED"));

            // Assert
            mockMvc.perform(get("/api/notifications").header("Authorization", bearer(tasker)))
                    .andExpect(status().isUnauthorized());

            // Assert
            mockMvc.perform(post("/api/auth/refresh")
                            .cookie(new Cookie(RefreshTokenCookie.NAME, tasker.refreshToken())))
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

            // Assert
            assertThat(offerRepository.findById(offerId).orElseThrow().getStatus())
                    .isEqualTo(OfferStatus.PENDING);

            // Assert
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

            // Act + Assert
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

    private void promoteToAdmin(String email) {
        AdminBootstrap bootstrap = new AdminBootstrap(userRepository, roleRepository, email);
        transactionTemplate.executeWithoutResult(status -> bootstrap.promoteConfiguredAdmin());
    }

    private UUID publishedTask() {
        UUID taskId = taskService.createTask(client.userId(), new CreateTaskRequest(
                "Popravka slavine", "Opis", category.getId(), municipality.getId(), "Zmaja od Bosne 12", null, null,
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
