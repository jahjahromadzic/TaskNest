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

    @Test
    @DisplayName("The public listing stays reachable without a token")
    void browse_isPublic_whenNoTokenIsSent() throws Exception {
        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("An anonymous call to /mine is 401, not 403")
    void mine_isUnauthorised_whenAnonymous() throws Exception {
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
        AuthResponse client = register("listing.client@test.ba");

        mockMvc.perform(get("/api/tasks/matching")
                        .header("Authorization", "Bearer " + client.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("A single task is reachable without a token")
    void getOne_isPublic_whenNoTokenIsSent() throws Exception {
        mockMvc.perform(get("/api/tasks/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

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

    @Test
    @DisplayName("A paged response exposes only the documented fields")
    void browse_returnsStablePageShape() throws Exception {
        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber())
                .andExpect(jsonPath("$.first").isBoolean())
                .andExpect(jsonPath("$.last").isBoolean());
    }

    @Test
    @DisplayName("Spring Data internals do not leak into the response")
    void browse_doesNotLeakSpringDataInternals() throws Exception {
        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageable").doesNotExist())
                .andExpect(jsonPath("$.sort").doesNotExist())
                .andExpect(jsonPath("$.numberOfElements").doesNotExist())
                .andExpect(jsonPath("$.empty").doesNotExist())
                .andExpect(jsonPath("$.number").doesNotExist());
    }

    @Test
    @DisplayName("Every paged endpoint uses the same shape")
    void allPagedEndpoints_useTheSameShape() throws Exception {
        AuthResponse client = register("shape.client@test.ba");
        String token = client.token();

        mockMvc.perform(get("/api/tasks/mine").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").exists())
                .andExpect(jsonPath("$.pageable").doesNotExist());
    }

    private AuthResponse register(String email) {
        return authService.register(
                new RegisterRequest(email, "password123", "Test", "User", null));
    }
}
