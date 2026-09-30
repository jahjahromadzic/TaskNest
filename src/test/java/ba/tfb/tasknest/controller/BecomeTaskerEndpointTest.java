package ba.tfb.tasknest.controller;

import ba.tfb.tasknest.AbstractIntegrationTest;
import ba.tfb.tasknest.dto.auth.AuthResponse;
import ba.tfb.tasknest.dto.auth.RegisterRequest;
import ba.tfb.tasknest.entity.Category;
import ba.tfb.tasknest.entity.Municipality;
import ba.tfb.tasknest.entity.enums.RoleName;
import ba.tfb.tasknest.repository.CategoryRepository;
import ba.tfb.tasknest.repository.MunicipalityRepository;
import ba.tfb.tasknest.repository.TaskerProfileRepository;
import ba.tfb.tasknest.repository.UserRepository;
import ba.tfb.tasknest.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class BecomeTaskerEndpointTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthService authService;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private MunicipalityRepository municipalityRepository;
    @Autowired private TaskerProfileRepository taskerProfileRepository;

    private AuthResponse client;
    private Category category;
    private Municipality municipality;

    @BeforeEach
    void setUp() {
        client = authService.register(new RegisterRequest("become@test.ba", "password123", "Emir", "Kovačević", null));
        category = categoryRepository.findAll().stream().filter(Category::isActive).findFirst().orElseThrow();
        municipality = municipalityRepository.findAll().getFirst();
    }

    @Test
    @DisplayName("Saving the tasker profile grants the tasker role and stores everything that was filled in")
    void becomeTasker_savesTheProfileAndGrantsTheRole() throws Exception {
        // Act
        becomeTasker("""
                {"headline": "  Plumber with 10 years of experience ", "bio": "I bring my own tools.",
                 "categoryIds": ["%s"], "municipalityIds": ["%s"]}
                """.formatted(category.getId(), municipality.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(2));

        // Assert
        assertThat(isTasker()).isTrue();
        transactionTemplate.executeWithoutResult(status -> {
            var profile = taskerProfileRepository.findByUser(userRepository.findById(client.userId()).orElseThrow()).orElseThrow();
            assertThat(profile.getHeadline()).isEqualTo("Plumber with 10 years of experience");
            assertThat(profile.getBio()).isEqualTo("I bring my own tools.");
            assertThat(profile.getCategories()).extracting(Category::getId).containsExactly(category.getId());
            assertThat(profile.getMunicipalities()).extracting(Municipality::getId).containsExactly(municipality.getId());
            assertThat(profile.isVerified()).isFalse();
        });
    }

    @Test
    @DisplayName("Without a headline, a category or a municipality the request is refused and nothing changes")
    void becomeTasker_refusesAnIncompleteProfile() throws Exception {
        becomeTasker("""
                {"headline": " ", "categoryIds": ["%s"], "municipalityIds": ["%s"]}
                """.formatted(category.getId(), municipality.getId()))
                .andExpect(status().isBadRequest());
        becomeTasker("""
                {"headline": "Plumber", "categoryIds": [], "municipalityIds": ["%s"]}
                """.formatted(municipality.getId()))
                .andExpect(status().isBadRequest());
        becomeTasker("""
                {"headline": "Plumber", "categoryIds": ["%s"]}
                """.formatted(category.getId()))
                .andExpect(status().isBadRequest());

        assertThat(isTasker()).isFalse();
        assertThat(taskerProfileRepository.count()).isZero();
    }

    @Test
    @DisplayName("An unknown category rolls everything back, so the client does not become a half-finished tasker")
    void becomeTasker_rollsBack_whenACategoryDoesNotExist() throws Exception {
        becomeTasker("""
                {"headline": "Plumber", "categoryIds": ["%s"], "municipalityIds": ["%s"]}
                """.formatted(UUID.randomUUID(), municipality.getId()))
                .andExpect(status().isNotFound());

        assertThat(isTasker()).isFalse();
        assertThat(taskerProfileRepository.count()).isZero();
    }

    @Test
    @DisplayName("Becoming a tasker twice is refused")
    void becomeTasker_refusesAnExistingTasker() throws Exception {
        String body = """
                {"headline": "Plumber", "categoryIds": ["%s"], "municipalityIds": ["%s"]}
                """.formatted(category.getId(), municipality.getId());
        becomeTasker(body).andExpect(status().isOk());

        becomeTasker(body).andExpect(status().isBadRequest());
        assertThat(taskerProfileRepository.count()).isEqualTo(1);
    }

    private ResultActions becomeTasker(String body) throws Exception {
        return mockMvc.perform(post("/api/auth/activate-tasker")
                .header("Authorization", "Bearer " + client.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private boolean isTasker() {
        return Boolean.TRUE.equals(transactionTemplate.execute(status ->
                userRepository.findById(client.userId()).orElseThrow().getRoles().stream()
                        .anyMatch(role -> role.getName() == RoleName.TASKER)));
    }
}
