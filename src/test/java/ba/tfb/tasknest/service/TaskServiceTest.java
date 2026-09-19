package ba.tfb.tasknest.service;

import ba.tfb.tasknest.domain.InvalidTaskTransitionException;
import ba.tfb.tasknest.dto.task.CreateTaskRequest;
import ba.tfb.tasknest.dto.task.TaskResponse;
import ba.tfb.tasknest.entity.Category;
import ba.tfb.tasknest.entity.Municipality;
import ba.tfb.tasknest.entity.Task;
import ba.tfb.tasknest.entity.User;
import ba.tfb.tasknest.entity.enums.TaskStatus;
import ba.tfb.tasknest.exception.BusinessRuleException;
import ba.tfb.tasknest.exception.NotResourceOwnerException;
import ba.tfb.tasknest.exception.ResourceNotFoundException;
import ba.tfb.tasknest.repository.CategoryRepository;
import ba.tfb.tasknest.repository.MunicipalityRepository;
import ba.tfb.tasknest.repository.TaskRepository;
import ba.tfb.tasknest.repository.UserRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Poslovna pravila TaskService-a, bez Springa i baze.
 * <p>
 * Hibernate i transakcijski mehanizmi (dirty checking, flush, optimistic
 * locking) se ovdje namjerno ne testiraju - to pokrivaju integracioni testovi.
 */
@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID OTHER_USER_ID = UUID.randomUUID();
    private static final UUID TASK_ID = UUID.randomUUID();
    private static final UUID CATEGORY_ID = UUID.randomUUID();
    private static final UUID MUNICIPALITY_ID = UUID.randomUUID();

    @Mock private TaskRepository taskRepository;
    @Mock private UserRepository userRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private MunicipalityRepository municipalityRepository;
    @Mock private OfferService offerService;

    @InjectMocks private TaskService taskService;

    @Nested
    class CreateTask {

        @Test
        void createTask_throwsNotFound_whenClientDoesNotExist() {
            // Arrange
            when(userRepository.findById(CLIENT_ID)).thenReturn(Optional.empty());

            // Act + Assert
            assertThatThrownBy(() -> taskService.createTask(CLIENT_ID, aRequest()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User");
        }

        @Test
        void createTask_throwsNotFound_whenCategoryDoesNotExist() {
            // Arrange
            when(userRepository.findById(CLIENT_ID)).thenReturn(Optional.of(aClient()));
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.empty());

            // Act + Assert
            assertThatThrownBy(() -> taskService.createTask(CLIENT_ID, aRequest()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Category");
        }

        @Test
        void createTask_throwsBusinessRule_whenCategoryIsInactive() {
            // Arrange
            Category inactive = aCategory();
            inactive.setActive(false);
            when(userRepository.findById(CLIENT_ID)).thenReturn(Optional.of(aClient()));
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(inactive));

            // Act + Assert
            assertThatThrownBy(() -> taskService.createTask(CLIENT_ID, aRequest()))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("not active");
        }

        @Test
        void createTask_throwsNotFound_whenMunicipalityDoesNotExist() {
            // Arrange
            when(userRepository.findById(CLIENT_ID)).thenReturn(Optional.of(aClient()));
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(aCategory()));
            when(municipalityRepository.findById(MUNICIPALITY_ID)).thenReturn(Optional.empty());

            // Act + Assert
            assertThatThrownBy(() -> taskService.createTask(CLIENT_ID, aRequest()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Municipality");
        }

        @Test
        void createTask_startsInDraft_whenInputIsValid() {
            // Arrange
            when(userRepository.findById(CLIENT_ID)).thenReturn(Optional.of(aClient()));
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(aCategory()));
            when(municipalityRepository.findById(MUNICIPALITY_ID)).thenReturn(Optional.of(aMunicipality()));
            when(taskRepository.save(any(Task.class))).thenAnswer(call -> call.getArgument(0));

            // Act
            TaskResponse response = taskService.createTask(CLIENT_ID, aRequest());

            // Assert - novi task nikad ne krece kao objavljen
            assertThat(response.status()).isEqualTo(TaskStatus.DRAFT);
            assertThat(response.publishedAt()).isNull();
            assertThat(response.expiresAt()).isNull();
            assertThat(response.title()).isEqualTo("Popravka slavine");
            assertThat(response.categoryName()).isEqualTo("Vodoinstalacije");
            assertThat(response.municipalityName()).isEqualTo("Centar");
        }
    }

    @Nested
    class PublishTask {

        @Test
        void publishTask_setsPublishedAtAndExpiresAt_whenTaskIsDraft() {
            // Arrange
            Task task = aTask(TaskStatus.DRAFT, aClient());
            when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

            // Act
            TaskResponse response = taskService.publishTask(TASK_ID, CLIENT_ID);

            // Assert
            assertThat(response.status()).isEqualTo(TaskStatus.PUBLISHED);
            assertThat(response.publishedAt()).isNotNull();
            // Rok vrijedi 30 dana od objave; poredi se relativno, bez ovisnosti o satu.
            assertThat(response.expiresAt()).isEqualTo(response.publishedAt().plusDays(30));
        }

        @Test
        void publishTask_throwsNotResourceOwner_whenTaskBelongsToAnotherUser() {
            // Arrange
            Task task = aTask(TaskStatus.DRAFT, aClient());
            when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

            // Act + Assert
            assertThatThrownBy(() -> taskService.publishTask(TASK_ID, OTHER_USER_ID))
                    .isInstanceOf(NotResourceOwnerException.class);
        }

        @Test
        void publishTask_throwsInvalidTransition_whenTaskAlreadyPublished() {
            // Arrange
            Task task = aTask(TaskStatus.PUBLISHED, aClient());
            when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

            // Act + Assert
            assertThatThrownBy(() -> taskService.publishTask(TASK_ID, CLIENT_ID))
                    .isInstanceOf(InvalidTaskTransitionException.class);
        }

        @Test
        void publishTask_throwsInvalidTransition_whenTaskIsCancelled() {
            // Arrange
            Task task = aTask(TaskStatus.CANCELLED, aClient());
            when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

            // Act + Assert
            assertThatThrownBy(() -> taskService.publishTask(TASK_ID, CLIENT_ID))
                    .isInstanceOf(InvalidTaskTransitionException.class);
        }
    }

    @Nested
    class CancelTask {

        @Test
        void cancelTask_throwsNotResourceOwner_whenTaskBelongsToAnotherUser() {
            // Arrange
            Task task = aTask(TaskStatus.PUBLISHED, aClient());
            when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

            // Act + Assert
            assertThatThrownBy(() -> taskService.cancelTask(TASK_ID, OTHER_USER_ID))
                    .isInstanceOf(NotResourceOwnerException.class);

            // Vlasnistvo se provjerava prije ikakvog ciscenja
            verify(offerService, never()).rejectActiveOffers(any());
        }

        @Test
        void cancelTask_throwsInvalidTransition_whenTaskIsInTerminalState() {
            // Arrange
            Task task = aTask(TaskStatus.CANCELLED, aClient());
            when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

            // Act + Assert
            assertThatThrownBy(() -> taskService.cancelTask(TASK_ID, CLIENT_ID))
                    .isInstanceOf(InvalidTaskTransitionException.class);
        }

        @Test
        void cancelTask_throwsInvalidTransition_whenTaskIsCompleted() {
            // Arrange - COMPLETED smije samo u CLOSED, ne u CANCELLED
            Task task = aTask(TaskStatus.COMPLETED, aClient());
            when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

            // Act + Assert
            assertThatThrownBy(() -> taskService.cancelTask(TASK_ID, CLIENT_ID))
                    .isInstanceOf(InvalidTaskTransitionException.class);
        }

        @Test
        void cancelTask_clearsAcceptedOfferAndDelegatesCleanup_whenTaskIsAssigned() {
            // Arrange
            Task task = aTask(TaskStatus.ASSIGNED, aClient());
            task.setAcceptedOffer(new ba.tfb.tasknest.entity.Offer());
            when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

            // Act
            TaskResponse response = taskService.cancelTask(TASK_ID, CLIENT_ID);

            // Assert
            assertThat(response.status()).isEqualTo(TaskStatus.CANCELLED);
            assertThat(task.getAcceptedOffer())
                    .as("otkazan task ne smije pokazivati na prihvacenu ponudu")
                    .isNull();

            // Ovdje je verify opravdan: ciscenje ponuda i razgovora je vlasnistvo
            // OfferService-a, pa je samo delegiranje ono sto TaskService obecava.
            // Da li ponude stvarno odu u REJECTED provjerava OfferServiceTest.
            verify(offerService).rejectActiveOffers(task);
        }
    }

    @Nested
    class GetTask {

        @Test
        void getTask_throwsNotFound_whenTaskDoesNotExist() {
            // Arrange
            when(taskRepository.findById(TASK_ID)).thenReturn(Optional.empty());

            // Act + Assert
            assertThatThrownBy(() -> taskService.getTask(TASK_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Task");
        }

        @Test
        void getTask_returnsTask_whenTaskExists() {
            // Arrange
            Task task = aTask(TaskStatus.PUBLISHED, aClient());
            task.setId(TASK_ID);
            when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

            // Act
            TaskResponse response = taskService.getTask(TASK_ID);

            // Assert
            assertThat(response.id()).isEqualTo(TASK_ID);
            assertThat(response.status()).isEqualTo(TaskStatus.PUBLISHED);
            assertThat(response.clientName()).isEqualTo("Amra Client");
        }
    }

    // ---------- fixtures ----------

    private CreateTaskRequest aRequest() {
        return new CreateTaskRequest(
                "Popravka slavine",
                "Curi ispod sudopera",
                CATEGORY_ID,
                MUNICIPALITY_ID,
                new BigDecimal("50.00"));
    }

    private User aClient() {
        User client = new User();
        client.setId(CLIENT_ID);
        client.setEmail("amra@test.ba");
        client.setFirstName("Amra");
        client.setLastName("Client");
        return client;
    }

    private Category aCategory() {
        Category category = new Category();
        category.setId(CATEGORY_ID);
        category.setName("Vodoinstalacije");
        category.setActive(true);
        return category;
    }

    private Municipality aMunicipality() {
        Municipality municipality = new Municipality();
        municipality.setId(MUNICIPALITY_ID);
        municipality.setName("Centar");
        return municipality;
    }

    private Task aTask(TaskStatus status, User client) {
        Task task = new Task();
        task.setId(TASK_ID);
        task.setClient(client);
        task.setCategory(aCategory());
        task.setMunicipality(aMunicipality());
        task.setTitle("Popravka slavine");
        task.setStatus(status);
        if (status != TaskStatus.DRAFT) {
            task.setPublishedAt(LocalDateTime.now().minusDays(1));
            task.setExpiresAt(LocalDateTime.now().plusDays(29));
        }
        return task;
    }
}
