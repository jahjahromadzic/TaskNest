package ba.tfb.tasknest.service;

import ba.tfb.tasknest.domain.InvalidTaskTransitionException;
import ba.tfb.tasknest.dto.task.CreateTaskRequest;
import ba.tfb.tasknest.dto.task.TaskResponse;
import ba.tfb.tasknest.dto.task.TaskSummaryResponse;
import ba.tfb.tasknest.entity.Category;
import ba.tfb.tasknest.entity.Municipality;
import ba.tfb.tasknest.entity.Offer;
import ba.tfb.tasknest.entity.Task;
import ba.tfb.tasknest.entity.User;
import ba.tfb.tasknest.entity.enums.OfferStatus;
import ba.tfb.tasknest.entity.enums.TaskStatus;
import ba.tfb.tasknest.exception.BusinessRuleException;
import ba.tfb.tasknest.geo.GeoPoint;
import ba.tfb.tasknest.geo.Geocoder;
import ba.tfb.tasknest.messaging.TaskExpiredEvent;
import ba.tfb.tasknest.exception.NotResourceOwnerException;
import ba.tfb.tasknest.exception.ResourceNotFoundException;
import ba.tfb.tasknest.repository.CategoryRepository;
import ba.tfb.tasknest.repository.MunicipalityRepository;
import ba.tfb.tasknest.repository.TaskRepository;
import ba.tfb.tasknest.repository.UserRepository;
import ba.tfb.tasknest.repository.projection.TaskStatusCount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID OTHER_USER_ID = UUID.randomUUID();
    private static final UUID TASK_ID = UUID.randomUUID();
    private static final UUID TASKER_ID = UUID.randomUUID();
    private static final UUID OFFER_ID = UUID.randomUUID();
    private static final UUID CATEGORY_ID = UUID.randomUUID();
    private static final UUID MUNICIPALITY_ID = UUID.randomUUID();
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 15, 12, 0);

    @Mock private TaskRepository taskRepository;
    @Mock private UserRepository userRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private MunicipalityRepository municipalityRepository;
    @Mock private OfferService offerService;
    @Mock private TaskerProfileService taskerProfileService;
    @Mock private NotificationService notificationService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private Geocoder geocoder;

    private final Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    private TaskService taskService;

    @BeforeEach
    void setUp() {
        taskService = new TaskService(taskRepository, userRepository, categoryRepository,
                municipalityRepository, offerService, taskerProfileService, notificationService,
                eventPublisher, clock, geocoder);
    }

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
            when(geocoder.geocode("Zmaja od Bosne 12", "Centar")).thenReturn(Optional.of(new GeoPoint(
                    new BigDecimal("43.854947"), new BigDecimal("18.393707"))));
            when(taskRepository.save(any(Task.class))).thenAnswer(call -> call.getArgument(0));

            // Act
            TaskResponse response = taskService.createTask(CLIENT_ID, aRequest());

            // Assert
            ArgumentCaptor<Task> saved = ArgumentCaptor.forClass(Task.class);
            verify(taskRepository).save(saved.capture());
            assertThat(saved.getValue().getAddressLine()).isEqualTo("Zmaja od Bosne 12");
            assertThat(saved.getValue().getLatitude()).isEqualByComparingTo("43.854947");
            assertThat(saved.getValue().getLongitude()).isEqualByComparingTo("18.393707");
            assertThat(response.status()).isEqualTo(TaskStatus.DRAFT);
            assertThat(response.publishedAt()).isNull();
            assertThat(response.expiresAt()).isNull();
            assertThat(response.title()).isEqualTo("Popravka slavine");
            assertThat(response.categoryName()).isEqualTo("Vodoinstalacije");
            assertThat(response.municipalityName()).isEqualTo("Centar");
        }

        @Test
        void createTask_throwsBusinessRule_whenAddressIsNotFound() {
            // Arrange
            when(userRepository.findById(CLIENT_ID)).thenReturn(Optional.of(aClient()));
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(aCategory()));
            when(municipalityRepository.findById(MUNICIPALITY_ID)).thenReturn(Optional.of(aMunicipality()));
            when(geocoder.geocode("Zmaja od Bosne 12", "Centar")).thenReturn(Optional.empty());

            // Act + Assert
            assertThatThrownBy(() -> taskService.createTask(CLIENT_ID, aRequest()))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("The address could not be found");
            verify(taskRepository, never()).save(any());
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
            assertThat(response.publishedAt()).isEqualTo(NOW);
            assertThat(response.expiresAt()).isEqualTo(NOW.plusDays(30));
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
            // Arrange
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
                    .as("a cancelled task must not point to an accepted offer")
                    .isNull();

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
            assertThatThrownBy(() -> taskService.getTask(TASK_ID, CLIENT_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Task");
        }

        @Test
        void getTask_returnsTask_whenTaskIsPublished() {
            // Arrange
            Task task = aTask(TaskStatus.PUBLISHED, aClient());
            task.setId(TASK_ID);
            when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

            // Act
            TaskResponse response = taskService.getTask(TASK_ID, null);

            // Assert
            assertThat(response.id()).isEqualTo(TASK_ID);
            assertThat(response.status()).isEqualTo(TaskStatus.PUBLISHED);
            assertThat(response.clientName()).isEqualTo("Amra Client");
            assertThat(response.clientId()).isEqualTo(CLIENT_ID);
            assertThat(response.assignedTaskerId()).isNull();
            assertThat(response.assignedTaskerName()).isNull();
        }

        @Test
        void getTask_namesTheAssignedTasker_whenAnOfferWasAccepted() {
            // Arrange
            Task task = anAssignedTask(TaskStatus.ASSIGNED);
            when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

            // Act
            TaskResponse response = taskService.getTask(TASK_ID, null);

            // Assert
            assertThat(response.assignedTaskerId()).isEqualTo(TASKER_ID);
            assertThat(response.assignedTaskerName()).isEqualTo("Emir Tasker");
        }

        @Test
        void getTask_returnsDraft_whenViewerIsTheOwner() {
            // Arrange
            Task task = aTask(TaskStatus.DRAFT, aClient());
            when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

            // Act
            TaskResponse response = taskService.getTask(TASK_ID, CLIENT_ID);

            // Assert
            assertThat(response.status()).isEqualTo(TaskStatus.DRAFT);
        }

        @Test
        void getTask_throwsNotFound_whenDraftIsViewedByAnotherUser() {
            // Arrange
            Task task = aTask(TaskStatus.DRAFT, aClient());
            when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

            // Act + Assert
            assertThatThrownBy(() -> taskService.getTask(TASK_ID, OTHER_USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void getTask_throwsNotFound_whenDraftIsViewedAnonymously() {
            // Arrange
            Task task = aTask(TaskStatus.DRAFT, aClient());
            when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

            // Act + Assert
            assertThatThrownBy(() -> taskService.getTask(TASK_ID, null))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class Listings {

        @Test
        void browseTasks_throwsBusinessRule_whenSortFieldIsUnknown() {
            // Act + Assert
            assertThatThrownBy(() -> taskService.browseTasks(null, null, null, null,
                    PageRequest.of(0, 20, Sort.by("nemaOvogPolja"))))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Cannot sort by 'nemaOvogPolja'")
                    .hasMessageContaining("publishedAt");
        }

        @Test
        void browseTasks_throwsBusinessRule_whenOneOfSeveralSortFieldsIsUnknown() {
            // Act + Assert
            assertThatThrownBy(() -> taskService.browseTasks(null, null, null, null,
                    PageRequest.of(0, 20, Sort.by("publishedAt").and(Sort.by("opis")))))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("opis");
        }

        @Test
        void browseTasks_delegatesToRepository_whenSortFieldIsAllowed() {
            // Arrange
            PageRequest pageable = PageRequest.of(0, 20, Sort.by("budget"));
            PageRequest emptyValuesLast = PageRequest.of(0, 20, Sort.by(Sort.Order.asc("budget").nullsLast()));
            when(taskRepository.findOpenTasks(eq(TaskStatus.PUBLISHED), any(), isNull(), eq(""), isNull(), eq(""),
                    eq(emptyValuesLast)))
                    .thenReturn(Page.empty());

            // Act
            Page<TaskSummaryResponse> page = taskService.browseTasks(null, null, null, null, pageable);

            // Assert
            assertThat(page).isEmpty();
        }

        @Test
        void getMatchingTasks_throwsBusinessRule_whenSortFieldIsUnknown() {
            // Act + Assert
            assertThatThrownBy(() -> taskService.getMatchingTasks(CLIENT_ID,
                    PageRequest.of(0, 20, Sort.by("drop table"))))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        void getMyTasks_throwsBusinessRule_whenSortFieldIsUnknown() {
            assertThatThrownBy(() -> taskService.getMyTasks(CLIENT_ID, null,
                    PageRequest.of(0, 20, Sort.by("nesto"))))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        void getMyTasks_asksForEveryStatus_whenNoStatusIsChosen() {
            // Arrange
            when(taskRepository.findByClientId(eq(CLIENT_ID), eq(EnumSet.allOf(TaskStatus.class)), any()))
                    .thenReturn(Page.empty());

            // Act
            Page<TaskSummaryResponse> page = taskService.getMyTasks(CLIENT_ID, Set.of(), PageRequest.of(0, 20));

            // Assert
            assertThat(page).isEmpty();
        }

        @Test
        void countMyTasks_reportsZero_forStatusesWithoutTasks() {
            // Arrange
            when(taskRepository.countByStatusForClient(CLIENT_ID))
                    .thenReturn(List.of(new TaskStatusCount(TaskStatus.PUBLISHED, 3)));

            // Act
            Map<TaskStatus, Long> counts = taskService.countMyTasks(CLIENT_ID);

            // Assert
            assertThat(counts).hasSize(TaskStatus.values().length);
            assertThat(counts.get(TaskStatus.PUBLISHED)).isEqualTo(3);
            assertThat(counts.get(TaskStatus.DRAFT)).isZero();
        }

        @Test
        void getAssignedTasks_throwsBusinessRule_whenSortFieldIsUnknown() {
            assertThatThrownBy(() -> taskService.getAssignedTasks(CLIENT_ID,
                    PageRequest.of(0, 20, Sort.by("nesto"))))
                    .isInstanceOf(BusinessRuleException.class);
        }
    }

    @Nested
    class ExpireOverdueTasks {

        @Test
        void expireOverdueTasks_movesTaskToExpired_whenDeadlineHasPassed() {
            // Arrange
            Task overdue = aTask(TaskStatus.PUBLISHED, aClient());
            overdue.setExpiresAt(NOW.minusDays(1));
            when(taskRepository.findByStatusAndExpiresAtBefore(TaskStatus.PUBLISHED, NOW))
                    .thenReturn(List.of(overdue));

            // Act
            int expired = taskService.expireOverdueTasks();

            // Assert
            assertThat(expired).isEqualTo(1);
            assertThat(overdue.getStatus()).isEqualTo(TaskStatus.EXPIRED);
        }

        @Test
        void expireOverdueTasks_publishesEventPerTask_soClientsCanBeNotified() {
            // Arrange
            Task first = aTask(TaskStatus.PUBLISHED, aClient());
            Task second = aTask(TaskStatus.PUBLISHED, aClient());
            when(taskRepository.findByStatusAndExpiresAtBefore(TaskStatus.PUBLISHED, NOW))
                    .thenReturn(List.of(first, second));

            // Act
            taskService.expireOverdueTasks();

            // Assert
            ArgumentCaptor<TaskExpiredEvent> events = ArgumentCaptor.forClass(TaskExpiredEvent.class);
            verify(eventPublisher, times(2)).publishEvent(events.capture());
            assertThat(events.getAllValues())
                    .allSatisfy(event -> assertThat(event.clientId()).isEqualTo(CLIENT_ID));
        }

        @Test
        void expireOverdueTasks_doesNothing_whenNoTaskIsOverdue() {
            // Arrange
            when(taskRepository.findByStatusAndExpiresAtBefore(TaskStatus.PUBLISHED, NOW))
                    .thenReturn(List.of());

            // Act
            int expired = taskService.expireOverdueTasks();

            // Assert
            assertThat(expired).isZero();
            verify(eventPublisher, never()).publishEvent(any(TaskExpiredEvent.class));
        }
    }

    @Nested
    class Deadlines {

        @Test
        void releaseStaleAssignment_reopens_whenAssignedBeforeTheCutoff() {
            // Arrange
            Task task = anAssignedTask(TaskStatus.ASSIGNED);
            task.setAssignedAt(NOW.minusDays(15));
            when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

            // Act
            boolean released = taskService.releaseStaleAssignment(TASK_ID, NOW.minusDays(14));

            // Assert
            assertThat(released).isTrue();
            verify(offerService).releaseStaleAssignment(task);
        }

        @Test
        void releaseStaleAssignment_skips_whenWorkStartedAfterTheTaskWasListed() {
            // Arrange
            Task task = anAssignedTask(TaskStatus.IN_PROGRESS);
            task.setAssignedAt(NOW.minusDays(15));
            when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

            // Act
            boolean released = taskService.releaseStaleAssignment(TASK_ID, NOW.minusDays(14));

            // Assert
            assertThat(released).isFalse();
            verify(offerService, never()).releaseStaleAssignment(any());
        }

        @Test
        void releaseStaleAssignment_skips_whenTheTaskWasReassignedMeanwhile() {
            // Arrange
            Task task = anAssignedTask(TaskStatus.ASSIGNED);
            task.setAssignedAt(NOW.minusDays(1));
            when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

            // Act
            boolean released = taskService.releaseStaleAssignment(TASK_ID, NOW.minusDays(14));

            // Assert
            assertThat(released).isFalse();
            verify(offerService, never()).releaseStaleAssignment(any());
        }

        @Test
        void autoCloseTask_closesAndCredits_whenCompletedBeforeTheCutoff() {
            // Arrange
            Task task = anAssignedTask(TaskStatus.COMPLETED);
            task.setCompletedAt(NOW.minusDays(8));
            when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

            // Act
            boolean closed = taskService.autoCloseTask(TASK_ID, NOW.minusDays(7));

            // Assert
            assertThat(closed).isTrue();
            assertThat(task.getStatus()).isEqualTo(TaskStatus.CLOSED);
            verify(taskerProfileService).recordCompletedJob(task.getAcceptedOffer().getTasker());
            verify(notificationService).notifyTaskAutoClosed(task, task.getAcceptedOffer().getTasker());
        }

        @Test
        void autoCloseTask_skips_whenTheClientClosedItMeanwhile() {
            // Arrange
            Task task = anAssignedTask(TaskStatus.CLOSED);
            task.setCompletedAt(NOW.minusDays(8));
            when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

            // Act
            boolean closed = taskService.autoCloseTask(TASK_ID, NOW.minusDays(7));

            // Assert
            assertThat(closed).isFalse();
            verify(taskerProfileService, never()).recordCompletedJob(any());
        }
    }

    @Nested
    class ReopenTask {

        @Test
        void reopenTask_releasesTheAssignment_whenCalledByTheOwner() {
            // Arrange
            Task task = anAssignedTask(TaskStatus.ASSIGNED);
            when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

            // Act
            taskService.reopenTask(TASK_ID, CLIENT_ID);

            // Assert
            verify(offerService).releaseAssignment(task);
        }

        @Test
        void reopenTask_throwsNotOwner_whenCalledByAnotherUser() {
            // Arrange
            Task task = anAssignedTask(TaskStatus.ASSIGNED);
            when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

            // Act + Assert
            assertThatThrownBy(() -> taskService.reopenTask(TASK_ID, OTHER_USER_ID))
                    .isInstanceOf(NotResourceOwnerException.class);
            verify(offerService, never()).releaseAssignment(any());
        }
    }

    @Nested
    class StartTask {

        @Test
        void startTask_movesTaskToInProgress_whenAssignedTaskerStartsIt() {
            // Arrange
            Task task = anAssignedTask(TaskStatus.ASSIGNED);
            when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

            // Act
            TaskResponse response = taskService.startTask(TASK_ID, TASKER_ID);

            // Assert
            assertThat(response.status()).isEqualTo(TaskStatus.IN_PROGRESS);
            assertThat(response.startedAt()).isEqualTo(NOW);
        }

        @Test
        void startTask_throwsNotOwner_whenCallerIsNotTheAssignedTasker() {
            // Arrange
            Task task = anAssignedTask(TaskStatus.ASSIGNED);
            when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

            // Act + Assert
            assertThatThrownBy(() -> taskService.startTask(TASK_ID, OTHER_USER_ID))
                    .isInstanceOf(NotResourceOwnerException.class);
        }

        @Test
        void startTask_throwsNotOwner_whenClientTriesToStartTheirOwnTask() {
            // Arrange
            Task task = anAssignedTask(TaskStatus.ASSIGNED);
            when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

            // Act + Assert
            assertThatThrownBy(() -> taskService.startTask(TASK_ID, CLIENT_ID))
                    .isInstanceOf(NotResourceOwnerException.class);
        }

        @Test
        void startTask_throwsNotOwner_whenTaskHasNoAcceptedOffer() {
            // Arrange
            Task task = aTask(TaskStatus.PUBLISHED, aClient());
            when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

            // Act + Assert
            assertThatThrownBy(() -> taskService.startTask(TASK_ID, TASKER_ID))
                    .isInstanceOf(NotResourceOwnerException.class);
        }

        @Test
        void startTask_throwsInvalidTransition_whenWorkIsAlreadyUnderway() {
            // Arrange
            Task task = anAssignedTask(TaskStatus.IN_PROGRESS);
            when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

            // Act + Assert
            assertThatThrownBy(() -> taskService.startTask(TASK_ID, TASKER_ID))
                    .isInstanceOf(InvalidTaskTransitionException.class);
        }
    }

    @Nested
    class CompleteTask {

        @Test
        void completeTask_movesTaskToCompleted_whenWorkIsInProgress() {
            // Arrange
            Task task = anAssignedTask(TaskStatus.IN_PROGRESS);
            when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

            // Act
            TaskResponse response = taskService.completeTask(TASK_ID, TASKER_ID);

            // Assert
            assertThat(response.status()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(response.completedAt()).isEqualTo(NOW);
        }

        @Test
        void completeTask_leavesReputationUntouched_becauseCompletionIsOnlyAClaim() {
            // Arrange
            Task task = anAssignedTask(TaskStatus.IN_PROGRESS);
            when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

            // Act
            taskService.completeTask(TASK_ID, TASKER_ID);

            // Assert
            verify(taskerProfileService, never()).recordCompletedJob(any());
        }

        @Test
        void completeTask_throwsInvalidTransition_whenWorkHasNotStarted() {
            // Arrange
            Task task = anAssignedTask(TaskStatus.ASSIGNED);
            when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

            // Act + Assert
            assertThatThrownBy(() -> taskService.completeTask(TASK_ID, TASKER_ID))
                    .isInstanceOf(InvalidTaskTransitionException.class);
        }

        @Test
        void completeTask_throwsNotOwner_whenCallerIsNotTheAssignedTasker() {
            // Arrange
            Task task = anAssignedTask(TaskStatus.IN_PROGRESS);
            when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

            // Act + Assert
            assertThatThrownBy(() -> taskService.completeTask(TASK_ID, OTHER_USER_ID))
                    .isInstanceOf(NotResourceOwnerException.class);
        }
    }

    @Nested
    class CloseTask {

        @Test
        void closeTask_movesTaskToClosed_whenClientConfirmsCompletedWork() {
            // Arrange
            Task task = anAssignedTask(TaskStatus.COMPLETED);
            when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

            // Act
            TaskResponse response = taskService.closeTask(TASK_ID, CLIENT_ID);

            // Assert
            assertThat(response.status()).isEqualTo(TaskStatus.CLOSED);
        }

        @Test
        void closeTask_creditsTheTasker_becauseTheClientConfirmedTheWork() {
            // Arrange
            Task task = anAssignedTask(TaskStatus.COMPLETED);
            when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

            // Act
            taskService.closeTask(TASK_ID, CLIENT_ID);

            // Assert
            ArgumentCaptor<User> credited = ArgumentCaptor.forClass(User.class);
            verify(taskerProfileService).recordCompletedJob(credited.capture());
            assertThat(credited.getValue().getId()).isEqualTo(TASKER_ID);
        }

        @Test
        void closeTask_archivesTheConversation_becauseTheJobIsOver() {
            // Arrange
            Task task = anAssignedTask(TaskStatus.COMPLETED);
            when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

            // Act
            taskService.closeTask(TASK_ID, CLIENT_ID);

            // Assert
            verify(offerService).archiveConversation(task.getAcceptedOffer());
        }

        @Test
        void closeTask_throwsInvalidTransition_whenWorkIsNotCompletedYet() {
            // Arrange
            Task task = anAssignedTask(TaskStatus.IN_PROGRESS);
            when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

            // Act + Assert
            assertThatThrownBy(() -> taskService.closeTask(TASK_ID, CLIENT_ID))
                    .isInstanceOf(InvalidTaskTransitionException.class);
        }

        @Test
        void closeTask_throwsNotOwner_whenTaskerTriesToCloseTheirOwnWork() {
            // Arrange
            Task task = anAssignedTask(TaskStatus.COMPLETED);
            when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

            // Act + Assert
            assertThatThrownBy(() -> taskService.closeTask(TASK_ID, TASKER_ID))
                    .isInstanceOf(NotResourceOwnerException.class);
        }
    }

    private CreateTaskRequest aRequest() {
        return new CreateTaskRequest(
                "Popravka slavine",
                "Curi ispod sudopera",
                CATEGORY_ID,
                MUNICIPALITY_ID, "Zmaja od Bosne 12",
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

    private User aTasker() {
        User tasker = new User();
        tasker.setId(TASKER_ID);
        tasker.setEmail("emir@test.ba");
        tasker.setFirstName("Emir");
        tasker.setLastName("Tasker");
        return tasker;
    }

    private Task anAssignedTask(TaskStatus status) {
        Task task = aTask(status, aClient());

        Offer acceptedOffer = new Offer();
        acceptedOffer.setId(OFFER_ID);
        acceptedOffer.setTask(task);
        acceptedOffer.setTasker(aTasker());
        acceptedOffer.setStatus(OfferStatus.ACCEPTED);
        task.setAcceptedOffer(acceptedOffer);

        return task;
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
