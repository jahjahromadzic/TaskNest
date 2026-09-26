package ba.tfb.tasknest.security;

import ba.tfb.tasknest.AbstractIntegrationTest;
import ba.tfb.tasknest.dto.auth.AuthResponse;
import ba.tfb.tasknest.dto.auth.RegisterRequest;
import ba.tfb.tasknest.dto.task.CreateTaskRequest;
import ba.tfb.tasknest.entity.Role;
import ba.tfb.tasknest.entity.User;
import ba.tfb.tasknest.entity.enums.RoleName;
import ba.tfb.tasknest.repository.*;
import ba.tfb.tasknest.service.AuthService;
import ba.tfb.tasknest.service.TaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthorizationIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthService authService;
    @Autowired private TaskService taskService;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private MunicipalityRepository municipalityRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private OfferRepository offerRepository;
    @Autowired private ConversationRepository conversationRepository;
    @Autowired private TaskerProfileRepository taskerProfileRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;

    private String clientToken;
    private String taskerToken;

    private UUID publishedTaskId;

    private UUID foreignTaskId;

    private UUID taskerOwnedTaskId;

    @BeforeEach
    void setUp() {
        AuthResponse client = register("authz.client@test.ba");
        clientToken = client.token();

        AuthResponse otherClient = register("authz.other@test.ba");
        foreignTaskId = createPublishedTask(otherClient.userId());

        AuthResponse tasker = register("authz.tasker@test.ba");
        authService.activateTaskerRole(tasker.userId());
        taskerOwnedTaskId = createDraftTask(tasker.userId());
        removeRole(tasker.userId(), RoleName.CLIENT);
        taskerToken = tasker.token();

        publishedTaskId = createPublishedTask(client.userId());
    }

    @AfterEach
    void tearDown() {
        conversationRepository.deleteAll();
        offerRepository.deleteAll();
        taskRepository.deleteAll();
        taskerProfileRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("A client-only account cannot submit an offer")
    void clientCannotSubmitOffer() throws Exception {
        mockMvc.perform(asUser(post("/api/tasks/" + foreignTaskId + "/offers"), clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":45.00,\"message\":\"ok\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A client-only account cannot withdraw an offer")
    void clientCannotWithdrawOffer() throws Exception {
        mockMvc.perform(asUser(post("/api/offers/" + UUID.randomUUID() + "/withdraw"), clientToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A client-only account cannot list tasker offers")
    void clientCannotListOwnOffersAsTasker() throws Exception {
        mockMvc.perform(asUser(get("/api/offers/mine"), clientToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A tasker-only account cannot create a task")
    void taskerCannotCreateTask() throws Exception {
        mockMvc.perform(asUser(post("/api/tasks"), taskerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTaskJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A tasker-only account cannot publish a task")
    void taskerCannotPublishTask() throws Exception {
        mockMvc.perform(asUser(post("/api/tasks/" + taskerOwnedTaskId + "/publish"), taskerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A tasker-only account cannot cancel a task")
    void taskerCannotCancelTask() throws Exception {
        mockMvc.perform(asUser(post("/api/tasks/" + taskerOwnedTaskId + "/cancel"), taskerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A tasker-only account cannot accept an offer")
    void taskerCannotAcceptOffer() throws Exception {
        mockMvc.perform(asUser(post("/api/offers/" + UUID.randomUUID() + "/accept"), taskerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A client can create a task")
    void clientCanCreateTask() throws Exception {
        mockMvc.perform(asUser(post("/api/tasks"), clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTaskJson()))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("A tasker can submit an offer")
    void taskerCanSubmitOffer() throws Exception {
        mockMvc.perform(asUser(post("/api/tasks/" + publishedTaskId + "/offers"), taskerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":45.00,\"message\":\"ok\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("A tasker can list their own offers")
    void taskerCanListOwnOffers() throws Exception {
        mockMvc.perform(asUser(get("/api/offers/mine"), taskerToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Activating the tasker role needs no particular role, only a login")
    void anyAuthenticatedUserCanActivateTaskerRole() throws Exception {
        AuthResponse fresh = register("authz.fresh@test.ba");

        mockMvc.perform(asUser(post("/api/auth/activate-tasker"), fresh.token()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Activating the tasker role without a token is 401, not 403")
    void anonymousCannotActivateTaskerRole() throws Exception {
        mockMvc.perform(post("/api/auth/activate-tasker"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A 403 carries a ProblemDetail body, like every other error")
    void forbiddenResponseIsProblemDetail() throws Exception {
        mockMvc.perform(asUser(get("/api/offers/mine"), clientToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    @DisplayName("A 401 carries a ProblemDetail body too, not an empty response")
    void unauthorisedResponseIsProblemDetail() throws Exception {
        mockMvc.perform(get("/api/offers/mine"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    private MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder builder, String token) {
        return builder.header("Authorization", "Bearer " + token);
    }

    private AuthResponse register(String email) {
        return authService.register(
                new RegisterRequest(email, "password123", "Test", "User", null));
    }

    private void removeRole(UUID userId, RoleName roleName) {
        User user = userRepository.findById(userId).orElseThrow();
        Role role = roleRepository.findByName(roleName).orElseThrow();
        user.getRoles().remove(role);
        userRepository.saveAndFlush(user);
    }

    private UUID createDraftTask(UUID ownerId) {
        return taskService.createTask(ownerId, taskRequest()).id();
    }

    private UUID createPublishedTask(UUID clientId) {
        UUID taskId = taskService.createTask(clientId, taskRequest()).id();
        taskService.publishTask(taskId, clientId);
        return taskId;
    }

    private CreateTaskRequest taskRequest() {
        return new CreateTaskRequest(
                "Seed task",
                "Created through the service, not the endpoint under test",
                categoryRepository.findAll().getFirst().getId(),
                municipalityRepository.findAll().getFirst().getId(),
                new java.math.BigDecimal("50.00"));
    }

    private String createTaskJson() {
        return "{\"title\":\"Test task\","
                + "\"categoryId\":\"" + categoryRepository.findAll().getFirst().getId() + "\","
                + "\"municipalityId\":\"" + municipalityRepository.findAll().getFirst().getId() + "\","
                + "\"budget\":50.00}";
    }
}
