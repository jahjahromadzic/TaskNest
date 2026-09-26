package ba.tfb.tasknest.repository;

import ba.tfb.tasknest.AbstractIntegrationTest;
import ba.tfb.tasknest.dto.task.TaskSummaryResponse;
import ba.tfb.tasknest.entity.Category;
import ba.tfb.tasknest.entity.Municipality;
import ba.tfb.tasknest.entity.Task;
import ba.tfb.tasknest.entity.User;
import ba.tfb.tasknest.entity.enums.AccountStatus;
import ba.tfb.tasknest.entity.enums.TaskStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TaskBrowseIntegrationTest extends AbstractIntegrationTest {

    @Autowired private TaskRepository taskRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private MunicipalityRepository municipalityRepository;

    private Category category;
    private Municipality municipality;
    private User client;

    @BeforeEach
    void setUp() {
        category = new Category();
        category.setName("Browse kategorija " + UUID.randomUUID());
        category.setActive(true);
        category = categoryRepository.saveAndFlush(category);

        municipality = new Municipality();
        municipality.setName("Browse opstina " + UUID.randomUUID());
        municipality = municipalityRepository.saveAndFlush(municipality);

        client = new User();
        client.setEmail("browse." + UUID.randomUUID() + "@test.ba");
        client.setPasswordHash("not-a-real-hash");
        client.setFirstName("Amra");
        client.setLastName("Client");
        client.setAccountStatus(AccountStatus.ACTIVE);
        client = userRepository.saveAndFlush(client);
    }

    @AfterEach
    void tearDown() {
        taskRepository.deleteAll();
        userRepository.deleteAll();
        categoryRepository.delete(category);
        municipalityRepository.delete(municipality);
    }

    @Test
    @DisplayName("A published task within its validity window is publicly listed")
    void findOpenTasks_returnsTask_whenPublishedAndNotExpired() {
        // Arrange
        persistTask(TaskStatus.PUBLISHED, LocalDateTime.now().plusDays(10));

        // Act
        Page<TaskSummaryResponse> page = browse();

        // Assert
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().getFirst().categoryName()).isEqualTo(category.getName());
        assertThat(page.getContent().getFirst().municipalityName()).isEqualTo(municipality.getName());
    }

    @Test
    @DisplayName("A task still marked PUBLISHED but past its expiry is not listed")
    void findOpenTasks_returnsEmpty_whenExpiryHasPassed() {
        // Arrange
        persistTask(TaskStatus.PUBLISHED, LocalDateTime.now().minusMinutes(1));

        // Act + Assert
        assertThat(browse().getContent()).isEmpty();
    }

    @Test
    @DisplayName("Drafts never appear in the public listing")
    void findOpenTasks_returnsEmpty_whenTaskIsDraft() {
        // Arrange
        persistTask(TaskStatus.DRAFT, null);

        // Act + Assert
        assertThat(browse().getContent()).isEmpty();
    }

    @Test
    @DisplayName("Cancelled and removed tasks never appear in the public listing")
    void findOpenTasks_returnsEmpty_whenTaskIsCancelledOrRemoved() {
        // Arrange
        persistTask(TaskStatus.CANCELLED, LocalDateTime.now().plusDays(10));
        persistTask(TaskStatus.REMOVED, LocalDateTime.now().plusDays(10));

        // Act + Assert
        assertThat(browse().getContent()).isEmpty();
    }

    @Test
    @DisplayName("An expired task is excluded from a tasker's matching feed too")
    void findMatchingTasks_returnsEmpty_whenExpiryHasPassed() {
        // Arrange
        persistTask(TaskStatus.PUBLISHED, LocalDateTime.now().minusMinutes(1));

        // Act
        Page<TaskSummaryResponse> page = taskRepository.findMatchingTasks(
                UUID.randomUUID(), TaskStatus.PUBLISHED, LocalDateTime.now(),
                PageRequest.of(0, 20));

        // Assert
        assertThat(page.getContent()).isEmpty();
    }

    private Page<TaskSummaryResponse> browse() {
        return taskRepository.findOpenTasks(
                TaskStatus.PUBLISHED, LocalDateTime.now(), null, null, PageRequest.of(0, 20));
    }

    private void persistTask(TaskStatus status, LocalDateTime expiresAt) {
        Task task = new Task();
        task.setClient(client);
        task.setCategory(category);
        task.setMunicipality(municipality);
        task.setTitle("Oglas " + UUID.randomUUID());
        task.setBudget(new BigDecimal("50.00"));
        task.setStatus(status);
        task.setExpiresAt(expiresAt);
        if (status != TaskStatus.DRAFT) {
            task.setPublishedAt(LocalDateTime.now().minusDays(1));
        }
        taskRepository.saveAndFlush(task);
    }
}
