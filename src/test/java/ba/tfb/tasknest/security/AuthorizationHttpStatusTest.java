package ba.tfb.tasknest.security;

import ba.tfb.tasknest.AbstractIntegrationTest;
import ba.tfb.tasknest.dto.auth.AuthResponse;
import ba.tfb.tasknest.dto.auth.RegisterRequest;
import ba.tfb.tasknest.dto.task.CreateTaskRequest;
import ba.tfb.tasknest.repository.*;
import ba.tfb.tasknest.service.AuthService;
import ba.tfb.tasknest.service.TaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Isti scenariji kao u {@link AuthorizationIntegrationTest}, ali preko pravog
 * servera - jer MockMvc ne izvrsava ERROR dispatch.
 * <p>
 * Konkretno: AccessDeniedHandler odgovara sa sendError(403), sto radi interni
 * forward na /error. Taj forward ponovo prolazi kroz security lanac bez
 * Authorization headera, pa ako /error nije javan, entry point pregazi 403 u
 * 401. MockMvc se zaustavi na sendError i pokaze 403 - dakle lazno zeleno.
 * Ovaj test je jedini koji tu razliku vidi.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthorizationHttpStatusTest extends AbstractIntegrationTest {

    /** Obican RestTemplate s ugasenim error handlerom - zanima nas status, ne izuzetak. */
    // JdkClientHttpRequestFactory, ne podrazumijevani HttpURLConnection: taj kod
    // statusa 401 tretira odgovor kao izazov za autentikaciju i ne vrati tijelo,
    // pa bi test tvrdio da ProblemDetail fali iako ga server posalje.
    private final RestTemplate restTemplate = new RestTemplate(new JdkClientHttpRequestFactory());

    @Value("${local.server.port}")
    private int port;
    @Autowired private AuthService authService;
    @Autowired private TaskService taskService;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private MunicipalityRepository municipalityRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private OfferRepository offerRepository;
    @Autowired private TaskerProfileRepository taskerProfileRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;

    @org.junit.jupiter.api.BeforeEach
    void doNotThrowOnErrorStatus() {
        restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }
        });
    }

    @AfterEach
    void tearDown() {
        offerRepository.deleteAll();
        taskRepository.deleteAll();
        taskerProfileRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Over real HTTP, a client without the tasker role gets 403, not 401")
    void clientWithoutTaskerRoleGetsForbiddenOverRealHttp() {
        AuthResponse owner = register("http.owner@test.ba");
        AuthResponse client = register("http.client@test.ba");
        UUID taskId = createPublishedTask(owner.userId());

        ResponseEntity<byte[]> response = submitOffer(taskId, client.token());

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(),
                "401 here means the /error forward was blocked and overwrote the 403");
        assertTrue(bodyOf(response).contains("\"detail\""),
                "403 must carry a ProblemDetail body, got: " + bodyOf(response));
    }

    @Test
    @DisplayName("Over real HTTP, no token still gets 401")
    void missingTokenStillGetsUnauthorised() {
        AuthResponse owner = register("http.owner2@test.ba");
        UUID taskId = createPublishedTask(owner.userId());

        ResponseEntity<byte[]> response = submitOffer(taskId, null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
                "an anonymous request must still be 401, not 403");
        assertTrue(bodyOf(response).contains("\"detail\""),
                "401 must carry a ProblemDetail body, got: " + bodyOf(response));
    }

    @Test
    @DisplayName("Over real HTTP, a tasker can submit an offer")
    void taskerCanSubmitOfferOverRealHttp() {
        AuthResponse owner = register("http.owner3@test.ba");
        AuthResponse tasker = register("http.tasker@test.ba");
        authService.activateTaskerRole(tasker.userId());
        UUID taskId = createPublishedTask(owner.userId());

        ResponseEntity<byte[]> response = submitOffer(taskId, tasker.token());

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    // ---------- helpers ----------

    /**
     * Vraca bajtove, ne String: RestTemplate ne mapira application/problem+json u
     * String pa bi tijelo ispalo null iako ga server posalje (provjereno curl-om).
     */
    private ResponseEntity<byte[]> submitOffer(UUID taskId, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }

        return restTemplate.exchange(
                "http://localhost:" + port + "/api/tasks/" + taskId + "/offers",
                HttpMethod.POST,
                new HttpEntity<>("{\"price\":45.00,\"message\":\"ok\"}", headers),
                byte[].class);
    }

    private String bodyOf(ResponseEntity<byte[]> response) {
        return response.getBody() == null
                ? ""
                : new String(response.getBody(), java.nio.charset.StandardCharsets.UTF_8);
    }

    private AuthResponse register(String email) {
        return authService.register(
                new RegisterRequest(email, "password123", "Test", "User", null));
    }

    private UUID createPublishedTask(UUID clientId) {
        CreateTaskRequest request = new CreateTaskRequest(
                "Seed task",
                "Created through the service",
                categoryRepository.findAll().getFirst().getId(),
                municipalityRepository.findAll().getFirst().getId(),
                new BigDecimal("50.00"));

        UUID taskId = taskService.createTask(clientId, request).id();
        taskService.publishTask(taskId, clientId);
        return taskId;
    }
}
