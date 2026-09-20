package ba.tfb.tasknest.security;

import ba.tfb.tasknest.AbstractIntegrationTest;
import ba.tfb.tasknest.dto.auth.AuthResponse;
import ba.tfb.tasknest.dto.auth.RegisterRequest;
import ba.tfb.tasknest.repository.RefreshTokenRepository;
import ba.tfb.tasknest.repository.TaskRepository;
import ba.tfb.tasknest.repository.TaskerProfileRepository;
import ba.tfb.tasknest.repository.UserRepository;
import ba.tfb.tasknest.service.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Endpointi za listanje: sta je javno, sta zahtijeva prijavu, i kako se
 * ponasaju na neispravan sort i pretjeranu velicinu strane.
 */
@AutoConfigureMockMvc
class TaskListingEndpointTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private TaskerProfileRepository taskerProfileRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;

    @AfterEach
    void tearDown() {
        taskRepository.deleteAll();
        taskerProfileRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ---------- javno vs prijavljeno ----------

    @Test
    @DisplayName("The public listing stays reachable without a token")
    void browse_isPublic_whenNoTokenIsSent() throws Exception {
        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("An anonymous call to /mine is 401, not 403")
    void mine_isUnauthorised_whenAnonymous() throws Exception {
        // 403 bi znacilo "nemas pravo" i klijent ne bi znao da treba na login.
        // Prije ispravke je /api/tasks/* propustao i literalne putanje.
        mockMvc.perform(get("/api/tasks/mine"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("An anonymous call to /matching is 401, not 403")
    void matching_isUnauthorised_whenAnonymous() throws Exception {
        mockMvc.perform(get("/api/tasks/matching"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("An anonymous call to /assigned is 401, not 403")
    void assigned_isUnauthorised_whenAnonymous() throws Exception {
        mockMvc.perform(get("/api/tasks/assigned"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("A client without the tasker role still gets 403 on /matching")
    void matching_isForbidden_whenCallerLacksTaskerRole() throws Exception {
        // Provjera role mora ostati na snazi - 401 samo za neprijavljene
        AuthResponse client = register("listing.client@test.ba");

        mockMvc.perform(get("/api/tasks/matching")
                        .header("Authorization", "Bearer " + client.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("A single task is reachable without a token")
    void getOne_isPublic_whenNoTokenIsSent() throws Exception {
        // Nepostojeci id daje 404, ne 401 - dakle putanja je javna
        mockMvc.perform(get("/api/tasks/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    // ---------- sort i velicina strane ----------

    @Test
    @DisplayName("An unknown sort field is 400 with a ProblemDetail, not 500")
    void browse_isBadRequest_whenSortFieldIsUnknown() throws Exception {
        mockMvc.perform(get("/api/tasks").param("sort", "nemaOvogPolja"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("nemaOvogPolja")));
    }

    @Test
    @DisplayName("The Swagger placeholder sort value is 400, not 500")
    void browse_isBadRequest_whenSortIsSwaggerPlaceholder() throws Exception {
        // Tacan zahtjev koji je prijavljen kao 500
        mockMvc.perform(get("/api/tasks").param("sort", "[\"string\"]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("An allowed sort field is accepted")
    void browse_isOk_whenSortFieldIsAllowed() throws Exception {
        mockMvc.perform(get("/api/tasks").param("sort", "budget,desc"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("An oversized page request is capped instead of honoured")
    void browse_capsPageSize_whenSizeIsAbsurd() throws Exception {
        mockMvc.perform(get("/api/tasks").param("size", "100000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(50));
    }

    // ---------- helpers ----------

    private AuthResponse register(String email) {
        return authService.register(
                new RegisterRequest(email, "password123", "Test", "User", null));
    }
}
