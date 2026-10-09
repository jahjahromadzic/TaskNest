package ba.tfb.tasknest.controller;

import ba.tfb.tasknest.AbstractIntegrationTest;
import ba.tfb.tasknest.dto.auth.AuthResponse;
import ba.tfb.tasknest.dto.auth.RegisterRequest;
import ba.tfb.tasknest.dto.task.CreateTaskRequest;
import ba.tfb.tasknest.entity.Category;
import ba.tfb.tasknest.entity.Municipality;
import ba.tfb.tasknest.repository.CategoryRepository;
import ba.tfb.tasknest.repository.MunicipalityRepository;
import ba.tfb.tasknest.service.AuthService;
import ba.tfb.tasknest.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TaskSearchEndpointTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthService authService;
    @Autowired private TaskService taskService;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private MunicipalityRepository municipalityRepository;

    private AuthResponse client;
    private Category cleaning;
    private Category repairs;
    private Municipality municipality;

    @BeforeEach
    void setUp() {
        client = authService.register(new RegisterRequest("search@test.ba", "password123", "Lejla", "Hodžić", null));
        List<Category> categories = categoryRepository.findAll().stream().filter(Category::isActive).toList();
        cleaning = categories.get(0);
        repairs = categories.get(1);
        municipality = named("Centar Sarajevo");

        publish("Čišćenje stana poslije renoviranja", "Dvosoban stan, 60 m2", cleaning);
        publish("Popravka veš mašine", "Perilica ne izbacuje vodu, 50% bubnja je puno", repairs);
        publish("Košenje trave", null, repairs);
        taskService.createTask(client.userId(), new CreateTaskRequest("Čišćenje podruma", null,
                cleaning.getId(), municipality.getId(), null));
    }

    @Test
    @DisplayName("Typing without the Bosnian letters still finds them, in any letter case")
    void search_ignoresDiacriticsAndCase() throws Exception {
        search("ciscenje").andExpect(jsonPath("$.content[*].title").value(contains("Čišćenje stana poslije renoviranja")));
        search("ČIŠĆENJE").andExpect(jsonPath("$.content[*].title").value(contains("Čišćenje stana poslije renoviranja")));
        search("kosenje").andExpect(jsonPath("$.content[*].title").value(contains("Košenje trave")));
    }

    @Test
    @DisplayName("Words from the description are found too")
    void search_looksIntoTheDescription() throws Exception {
        search("perilica").andExpect(jsonPath("$.content[*].title").value(contains("Popravka veš mašine")));
    }

    @Test
    @DisplayName("The search works together with the category filter")
    void search_combinesWithTheCategory() throws Exception {
        mockMvc.perform(get("/api/tasks").param("q", "a").param("categoryId", repairs.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].title")
                        .value(containsInAnyOrder("Popravka veš mašine", "Košenje trave")));
    }

    @Test
    @DisplayName("Percent signs and underscores are searched as text, not as wildcards")
    void search_treatsWildcardsAsText() throws Exception {
        search("50%").andExpect(jsonPath("$.content[*].title").value(contains("Popravka veš mašine")));
        search("%").andExpect(jsonPath("$.content[*].title").value(contains("Popravka veš mašine")));
        search("_").andExpect(jsonPath("$.content").value(empty()));
    }

    @Test
    @DisplayName("A blank search shows every open task, and drafts never appear")
    void search_blankShowsEverything() throws Exception {
        search("   ").andExpect(jsonPath("$.totalElements").value(3));
        search("podrum").andExpect(jsonPath("$.content").value(empty()));
    }

    @Test
    @DisplayName("Choosing a region shows the tasks from every municipality in it")
    void browse_filtersByRegion() throws Exception {
        Municipality tuzla = named("Tuzla");
        UUID taskId = taskService.createTask(client.userId(),
                new CreateTaskRequest("Selidba u Tuzli", null, repairs.getId(), tuzla.getId(), null)).id();
        taskService.publishTask(taskId, client.userId());

        mockMvc.perform(get("/api/tasks").param("region", "Tuzla Canton"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].title").value(contains("Selidba u Tuzli")));
        mockMvc.perform(get("/api/tasks").param("region", "Republika Srpska"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value(empty()));
        mockMvc.perform(get("/api/tasks").param("region", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4));
    }

    @Test
    @DisplayName("A search longer than 100 characters is refused")
    void search_refusesVeryLongText() throws Exception {
        mockMvc.perform(get("/api/tasks").param("q", "a".repeat(101)))
                .andExpect(status().isBadRequest());
    }

    private ResultActions search(String text) throws Exception {
        return mockMvc.perform(get("/api/tasks").param("q", text)).andExpect(status().isOk());
    }

    private Municipality named(String name) {
        return municipalityRepository.findAll().stream()
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst().orElseThrow();
    }

    private void publish(String title, String description, Category category) {
        UUID taskId = taskService.createTask(client.userId(),
                new CreateTaskRequest(title, description, category.getId(), municipality.getId(), null)).id();
        taskService.publishTask(taskId, client.userId());
    }
}
