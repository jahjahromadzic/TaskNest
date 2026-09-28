package ba.tfb.tasknest.security;

import ba.tfb.tasknest.AbstractIntegrationTest;
import ba.tfb.tasknest.dto.auth.AuthResponse;
import ba.tfb.tasknest.dto.auth.RegisterRequest;
import ba.tfb.tasknest.entity.Task;
import ba.tfb.tasknest.entity.User;
import ba.tfb.tasknest.entity.enums.TaskStatus;
import ba.tfb.tasknest.repository.CategoryRepository;
import ba.tfb.tasknest.repository.MunicipalityRepository;
import ba.tfb.tasknest.repository.TaskRepository;
import ba.tfb.tasknest.repository.UserRepository;
import ba.tfb.tasknest.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private MunicipalityRepository municipalityRepository;

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
    @DisplayName("Tasks without a budget come last whichever way the budget is sorted")
    void browse_putsTasksWithoutBudgetLast_inBothDirections() throws Exception {
        User client = userRepository.findById(register("budget.client@test.ba").userId()).orElseThrow();
        publishedTask(client, "Cheap", new BigDecimal("50"));
        publishedTask(client, "Open", null);
        publishedTask(client, "Expensive", new BigDecimal("200"));

        mockMvc.perform(get("/api/tasks").param("sort", "budget,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].title").value(
                        org.hamcrest.Matchers.contains("Expensive", "Cheap", "Open")));

        mockMvc.perform(get("/api/tasks").param("sort", "budget,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].title").value(
                        org.hamcrest.Matchers.contains("Cheap", "Expensive", "Open")));
    }

    @Test
    @DisplayName("A client can list only their own tasks in the chosen statuses")
    void mine_filtersByStatus_andShowsOnlyOwnTasks() throws Exception {
        AuthResponse amra = register("mine.amra@test.ba");
        User amraUser = userRepository.findById(amra.userId()).orElseThrow();
        User emina = userRepository.findById(register("mine.emina@test.ba").userId()).orElseThrow();
        task(amraUser, "Amra draft", TaskStatus.DRAFT);
        task(amraUser, "Amra open", TaskStatus.PUBLISHED);
        task(amraUser, "Amra closed", TaskStatus.CLOSED);
        task(emina, "Emina draft", TaskStatus.DRAFT);

        mockMvc.perform(get("/api/tasks/mine").param("status", "DRAFT")
                        .header("Authorization", "Bearer " + amra.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].title").value(org.hamcrest.Matchers.contains("Amra draft")));

        mockMvc.perform(get("/api/tasks/mine").param("status", "PUBLISHED", "CLOSED")
                        .header("Authorization", "Bearer " + amra.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/tasks/mine").header("Authorization", "Bearer " + amra.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    @DisplayName("A client gets the number of their own tasks in every status")
    void myCounts_countsOwnTasksPerStatus() throws Exception {
        AuthResponse amra = register("counts.amra@test.ba");
        User amraUser = userRepository.findById(amra.userId()).orElseThrow();
        User emina = userRepository.findById(register("counts.emina@test.ba").userId()).orElseThrow();
        task(amraUser, "One", TaskStatus.PUBLISHED);
        task(amraUser, "Two", TaskStatus.PUBLISHED);
        task(amraUser, "Three", TaskStatus.DRAFT);
        task(emina, "Not Amra's", TaskStatus.PUBLISHED);

        mockMvc.perform(get("/api/tasks/mine/counts").header("Authorization", "Bearer " + amra.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.PUBLISHED").value(2))
                .andExpect(jsonPath("$.DRAFT").value(1))
                .andExpect(jsonPath("$.CLOSED").value(0));
    }

    @Test
    @DisplayName("An unknown status in the filter is 400, not 500")
    void mine_isBadRequest_whenStatusIsUnknown() throws Exception {
        AuthResponse amra = register("badstatus.amra@test.ba");

        mockMvc.perform(get("/api/tasks/mine").param("status", "NEPOSTOJI")
                        .header("Authorization", "Bearer " + amra.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid value 'NEPOSTOJI' for parameter 'status'"));
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

    private void task(User client, String title, TaskStatus status) {
        Task task = new Task();
        task.setClient(client);
        task.setCategory(categoryRepository.findAll().getFirst());
        task.setMunicipality(municipalityRepository.findAll().getFirst());
        task.setTitle(title);
        task.setStatus(status);
        taskRepository.save(task);
    }

    private void publishedTask(User client, String title, BigDecimal budget) {
        Task task = new Task();
        task.setClient(client);
        task.setCategory(categoryRepository.findAll().getFirst());
        task.setMunicipality(municipalityRepository.findAll().getFirst());
        task.setTitle(title);
        task.setBudget(budget);
        task.setStatus(TaskStatus.PUBLISHED);
        task.setPublishedAt(LocalDateTime.now());
        task.setExpiresAt(LocalDateTime.now().plusDays(30));
        taskRepository.save(task);
    }

    private AuthResponse register(String email) {
        return authService.register(
                new RegisterRequest(email, "password123", "Test", "User", null));
    }
}
