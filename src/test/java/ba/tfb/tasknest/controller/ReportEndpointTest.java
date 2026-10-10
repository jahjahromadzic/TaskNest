package ba.tfb.tasknest.controller;

import ba.tfb.tasknest.AbstractIntegrationTest;
import ba.tfb.tasknest.dto.auth.AuthResponse;
import ba.tfb.tasknest.dto.auth.LoginRequest;
import ba.tfb.tasknest.dto.auth.RegisterRequest;
import ba.tfb.tasknest.dto.task.CreateTaskRequest;
import ba.tfb.tasknest.entity.Category;
import ba.tfb.tasknest.entity.Municipality;
import ba.tfb.tasknest.entity.enums.ReportStatus;
import ba.tfb.tasknest.entity.enums.RoleName;
import ba.tfb.tasknest.repository.CategoryRepository;
import ba.tfb.tasknest.repository.MunicipalityRepository;
import ba.tfb.tasknest.repository.ReportRepository;
import ba.tfb.tasknest.repository.RoleRepository;
import ba.tfb.tasknest.repository.UserRepository;
import ba.tfb.tasknest.service.AuthService;
import ba.tfb.tasknest.service.ReportService;
import ba.tfb.tasknest.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ReportEndpointTest extends AbstractIntegrationTest {

    private static final String SPAM = """
            {"reason": "SPAM", "comment": "Reklama za kredite"}
            """;

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthService authService;
    @Autowired private TaskService taskService;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private ReportRepository reportRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private MunicipalityRepository municipalityRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    private AuthResponse owner;
    private AuthResponse reporter;
    private AuthResponse admin;
    private UUID taskId;

    @BeforeEach
    void setUp() {
        owner = register("report.owner@test.ba", "Nermin", "Hadžić");
        reporter = register("report.reporter@test.ba", "Amra", "Hodžić");
        admin = registerAdmin("report.admin@test.ba");
        taskId = publishedTask(owner);
    }

    @Test
    @DisplayName("A reported task shows up for the admin with who reported it, why, and how many open reports it has")
    void reportTask_reachesTheAdmin() throws Exception {
        // Arrange
        AuthResponse second = register("report.second@test.ba", "Emina", "Begić");

        // Act
        reportTask(reporter, SPAM)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"));
        reportTask(second, """
                {"reason": "FRAUD"}
                """).andExpect(status().isCreated());

        // Assert
        mockMvc.perform(get("/api/admin/reports").param("status", "OPEN").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[1].reporterName").value("Amra Hodžić"))
                .andExpect(jsonPath("$.content[1].reason").value("SPAM"))
                .andExpect(jsonPath("$.content[1].comment").value("Reklama za kredite"))
                .andExpect(jsonPath("$.content[1].taskTitle").value("Zarada od kuće"))
                .andExpect(jsonPath("$.content[1].openReportsOnTarget").value(2));
        mockMvc.perform(get("/api/admin/stats").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.openReports").value(2));
    }

    @Test
    @DisplayName("Nobody can report their own task or themselves, and a draft cannot be reported at all")
    void report_refusesOwnTaskSelfAndDrafts() throws Exception {
        // Arrange
        UUID draft = taskService.createTask(owner.userId(), new CreateTaskRequest("Nacrt", null,
                category().getId(), municipality().getId(), "Zmaja od Bosne 12", null, null, null)).id();

        // Act & Assert
        reportTask(owner, SPAM)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("You cannot report your own task"));
        reportUser(reporter, reporter.userId(), SPAM)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("You cannot report yourself"));
        mockMvc.perform(post("/api/tasks/" + draft + "/reports").header("Authorization", bearer(reporter))
                        .contentType(MediaType.APPLICATION_JSON).content(SPAM))
                .andExpect(status().isNotFound());
        assertThat(reportRepository.count()).isZero();
    }

    @Test
    @DisplayName("The same person can report the same thing only once while it is open, and again after a dismissal")
    void report_isOncePerOpenReport() throws Exception {
        // Arrange
        reportTask(reporter, SPAM).andExpect(status().isCreated());

        // Act & Assert
        reportTask(reporter, SPAM)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("You have already reported this. An administrator will review it."));

        UUID first = reportRepository.findAll().getFirst().getId();
        mockMvc.perform(post("/api/admin/reports/" + first + "/dismiss").header("Authorization", bearer(admin)))
                .andExpect(status().isNoContent());
        reportTask(reporter, SPAM).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Two identical reports sent at the same moment create only one")
    void report_concurrentDuplicatesCreateOne() throws Exception {
        // Arrange
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            calls.add(() -> reportTask(reporter, SPAM).andReturn().getResponse().getStatus());
        }

        // Act
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> future : pool.invokeAll(calls)) {
            statuses.add(future.get());
        }
        pool.shutdown();

        // Assert
        assertThat(statuses).containsExactlyInAnyOrder(201, 400);
        assertThat(reportRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Other as a reason needs a description, and the reason itself is required")
    void report_validatesTheReason() throws Exception {
        reportUser(reporter, owner.userId(), """
                {"reason": "OTHER", "comment": "  "}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Describe the problem when the reason is Other"));
        reportUser(reporter, owner.userId(), """
                {"comment": "Bez razloga"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("reason")));
        reportUser(reporter, owner.userId(), """
                {"reason": "OTHER", "comment": "Traži plaćanje unaprijed"}
                """)
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Removing the task resolves every open report about it")
    void removingTheTask_resolvesItsReports() throws Exception {
        // Arrange
        reportTask(reporter, SPAM).andExpect(status().isCreated());
        reportTask(register("report.third@test.ba", "Haris", "Mehić"), SPAM).andExpect(status().isCreated());

        // Act
        mockMvc.perform(post("/api/admin/tasks/" + taskId + "/remove").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "Spam ili reklama"}
                                """))
                .andExpect(status().isOk());

        // Assert
        mockMvc.perform(get("/api/admin/reports").param("status", "RESOLVED").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].resolvedByName").value("Admin Test"))
                .andExpect(jsonPath("$.content[0].taskStatus").value("REMOVED"));
        assertThat(reportRepository.countByStatus(ReportStatus.OPEN)).isZero();
    }

    @Test
    @DisplayName("Suspending the user resolves the reports about them, but not the reports about other people")
    void suspendingTheUser_resolvesTheirReports() throws Exception {
        // Arrange
        AuthResponse innocent = register("report.innocent@test.ba", "Selma", "Karić");
        reportUser(reporter, owner.userId(), SPAM).andExpect(status().isCreated());
        reportUser(reporter, innocent.userId(), SPAM).andExpect(status().isCreated());

        // Act
        mockMvc.perform(post("/api/admin/users/" + owner.userId() + "/suspend").header("Authorization", bearer(admin)))
                .andExpect(status().isOk());

        // Assert
        mockMvc.perform(get("/api/admin/reports").param("status", "OPEN").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].reportedUserName").value("Selma Karić"));
        reportUser(register("report.late@test.ba", "Kenan", "Imamović"), owner.userId(), SPAM)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("This account is already suspended"));
    }

    @Test
    @DisplayName("One person can send at most ten reports a day")
    void report_isLimitedPerDay() throws Exception {
        for (int i = 0; i < ReportService.MAX_REPORTS_PER_DAY; i++) {
            AuthResponse target = register("report.target" + i + "@test.ba", "Meta", "Broj" + i);
            reportUser(reporter, target.userId(), SPAM).andExpect(status().isCreated());
        }

        reportTask(reporter, SPAM)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("You have sent too many reports today. Try again tomorrow."));
    }

    @Test
    @DisplayName("Only an admin sees the reports, and only a signed-in user can send one")
    void reports_areProtected() throws Exception {
        mockMvc.perform(get("/api/admin/reports").header("Authorization", bearer(reporter)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/tasks/" + taskId + "/reports").contentType(MediaType.APPLICATION_JSON).content(SPAM))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions reportTask(AuthResponse who, String json) throws Exception {
        return mockMvc.perform(post("/api/tasks/" + taskId + "/reports").header("Authorization", bearer(who))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions reportUser(AuthResponse who, UUID userId, String json) throws Exception {
        return mockMvc.perform(post("/api/users/" + userId + "/reports").header("Authorization", bearer(who))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private UUID publishedTask(AuthResponse client) {
        UUID id = taskService.createTask(client.userId(), new CreateTaskRequest("Zarada od kuće", "Javite se na WhatsApp",
                category().getId(), municipality().getId(),
                "Zmaja od Bosne 12", null, null, new BigDecimal("500"))).id();
        taskService.publishTask(id, client.userId());
        return id;
    }

    private Category category() {
        return categoryRepository.findAll().stream().filter(Category::isActive).findFirst().orElseThrow();
    }

    private Municipality municipality() {
        return municipalityRepository.findAll().getFirst();
    }

    private AuthResponse register(String email, String firstName, String lastName) {
        return authService.register(new RegisterRequest(email, "password123", firstName, lastName, null));
    }

    private AuthResponse registerAdmin(String email) {
        AuthResponse registered = register(email, "Admin", "Test");
        transactionTemplate.executeWithoutResult(status -> userRepository.findById(registered.userId()).orElseThrow()
                .getRoles().add(roleRepository.findByName(RoleName.ADMIN).orElseThrow()));
        return authService.login(new LoginRequest(email, "password123"));
    }

    private static String bearer(AuthResponse auth) {
        return "Bearer " + auth.token();
    }
}
