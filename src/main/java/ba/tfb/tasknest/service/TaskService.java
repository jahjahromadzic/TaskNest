package ba.tfb.tasknest.service;

import ba.tfb.tasknest.domain.TaskStateMachine;
import ba.tfb.tasknest.dto.task.CreateTaskRequest;
import ba.tfb.tasknest.dto.task.TaskResponse;
import ba.tfb.tasknest.dto.task.TaskSummaryResponse;
import ba.tfb.tasknest.entity.Category;
import ba.tfb.tasknest.entity.Municipality;
import ba.tfb.tasknest.entity.Offer;
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
import ba.tfb.tasknest.messaging.TaskExpiredEvent;
import ba.tfb.tasknest.messaging.TaskPublishedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskService {

    private static final int PUBLICATION_VALIDITY_DAYS = 30;

    private static final Set<String> SORTABLE_FIELDS =
            Set.of("publishedAt", "createdAt", "updatedAt", "expiresAt", "budget", "title", "status");

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final MunicipalityRepository municipalityRepository;
    private final OfferService offerService;
    private final TaskerProfileService taskerProfileService;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional
    public TaskResponse createTask(UUID clientId, CreateTaskRequest request) {
        User client = userRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("User", clientId));

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", request.categoryId()));

        if (!category.isActive()) {
            throw new BusinessRuleException("Category is not active: " + category.getName());
        }

        Municipality municipality = municipalityRepository.findById(request.municipalityId())
                .orElseThrow(() -> new ResourceNotFoundException("Municipality", request.municipalityId()));

        Task task = new Task();
        task.setClient(client);
        task.setCategory(category);
        task.setMunicipality(municipality);
        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setBudget(request.budget());
        task.setStatus(TaskStatus.DRAFT);

        return TaskResponse.from(taskRepository.save(task));
    }

    @Transactional
    public TaskResponse publishTask(UUID taskId, UUID clientId) {
        Task task = loadOwnedTask(taskId, clientId);

        TaskStateMachine.validateTransition(task.getStatus(), TaskStatus.PUBLISHED);

        LocalDateTime now = LocalDateTime.now(clock);
        task.setStatus(TaskStatus.PUBLISHED);
        task.setPublishedAt(now);
        task.setExpiresAt(now.plusDays(PUBLICATION_VALIDITY_DAYS));

        eventPublisher.publishEvent(new TaskPublishedEvent(
                task.getId(),
                task.getTitle(),
                task.getCategory().getId(),
                task.getMunicipality().getId(),
                task.getClient().getId()));

        return TaskResponse.from(task);
    }

    @Transactional
    public TaskResponse cancelTask(UUID taskId, UUID clientId) {
        Task task = loadOwnedTask(taskId, clientId);

        TaskStateMachine.validateTransition(task.getStatus(), TaskStatus.CANCELLED);

        task.setStatus(TaskStatus.CANCELLED);
        task.setAcceptedOffer(null);
        taskRepository.flush();

        offerService.rejectActiveOffers(task);

        return TaskResponse.from(task);
    }

    @Transactional
    public TaskResponse startTask(UUID taskId, UUID taskerId) {
        Task task = loadAssignedTask(taskId, taskerId);

        TaskStateMachine.validateTransition(task.getStatus(), TaskStatus.IN_PROGRESS);

        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setStartedAt(LocalDateTime.now(clock));

        notificationService.notifyTaskStarted(task);

        return TaskResponse.from(task);
    }

    @Transactional
    public TaskResponse completeTask(UUID taskId, UUID taskerId) {
        Task task = loadAssignedTask(taskId, taskerId);

        TaskStateMachine.validateTransition(task.getStatus(), TaskStatus.COMPLETED);

        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(LocalDateTime.now(clock));

        notificationService.notifyTaskCompleted(task);

        return TaskResponse.from(task);
    }

    @Transactional
    public TaskResponse closeTask(UUID taskId, UUID clientId) {
        Task task = loadOwnedTask(taskId, clientId);

        TaskStateMachine.validateTransition(task.getStatus(), TaskStatus.CLOSED);

        Offer acceptedOffer = requireAcceptedOffer(task);
        User tasker = acceptedOffer.getTasker();

        task.setStatus(TaskStatus.CLOSED);
        taskerProfileService.recordCompletedJob(tasker);

        offerService.archiveConversation(acceptedOffer);

        notificationService.notifyTaskClosed(task, tasker);

        return TaskResponse.from(task);
    }

    @Transactional
    public TaskResponse removeTask(UUID taskId, String reason) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

        TaskStateMachine.validateTransition(task.getStatus(), TaskStatus.REMOVED);

        task.setStatus(TaskStatus.REMOVED);
        task.setAcceptedOffer(null);
        taskRepository.flush();

        offerService.rejectActiveOffers(task);
        notificationService.notifyTaskRemoved(task, reason);

        return TaskResponse.from(task);
    }

    @Transactional
    public int expireOverdueTasks() {
        List<Task> overdue = taskRepository.findByStatusAndExpiresAtBefore(
                TaskStatus.PUBLISHED, LocalDateTime.now(clock));

        for (Task task : overdue) {
            TaskStateMachine.validateTransition(task.getStatus(), TaskStatus.EXPIRED);
            task.setStatus(TaskStatus.EXPIRED);

            eventPublisher.publishEvent(new TaskExpiredEvent(
                    task.getId(), task.getTitle(), task.getClient().getId()));
        }

        return overdue.size();
    }

    @Transactional(readOnly = true)
    public TaskResponse getTask(UUID taskId, UUID viewerId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

        if (task.getStatus() == TaskStatus.DRAFT
                && !task.getClient().getId().equals(viewerId)) {
            throw new ResourceNotFoundException("Task", taskId);
        }

        return TaskResponse.from(task);
    }

    private Task loadAssignedTask(UUID taskId, UUID taskerId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

        Offer acceptedOffer = task.getAcceptedOffer();

        if (acceptedOffer == null || !acceptedOffer.getTasker().getId().equals(taskerId)) {
            throw new NotResourceOwnerException("Task is not assigned to this user");
        }

        return task;
    }

    private Offer requireAcceptedOffer(Task task) {
        Offer acceptedOffer = task.getAcceptedOffer();

        if (acceptedOffer == null) {
            throw new IllegalStateException("Task " + task.getId() + " has no accepted offer");
        }

        return acceptedOffer;
    }

    private Task loadOwnedTask(UUID taskId, UUID clientId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

        if (!task.getClient().getId().equals(clientId)) {
            throw new NotResourceOwnerException("Task does not belong to this user");
        }

        return task;
    }

    @Transactional(readOnly = true)
    public Page<TaskSummaryResponse> browseTasks(UUID categoryId,
                                                 UUID municipalityId,
                                                 Pageable pageable) {
        requireSortableFields(pageable);
        return taskRepository.findOpenTasks(TaskStatus.PUBLISHED, LocalDateTime.now(clock),
                categoryId, municipalityId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<TaskSummaryResponse> getMatchingTasks(UUID taskerId, Pageable pageable) {
        requireSortableFields(pageable);
        return taskRepository.findMatchingTasks(
                taskerId, TaskStatus.PUBLISHED, LocalDateTime.now(clock), pageable);
    }

    @Transactional(readOnly = true)
    public Page<TaskSummaryResponse> getMyTasks(UUID clientId, Pageable pageable) {
        requireSortableFields(pageable);
        return taskRepository.findByClientId(clientId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<TaskSummaryResponse> getAssignedTasks(UUID taskerId, Pageable pageable) {
        requireSortableFields(pageable);
        return taskRepository.findAssignedToTasker(taskerId, pageable);
    }

    private void requireSortableFields(Pageable pageable) {
        pageable.getSort().forEach(order -> {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new BusinessRuleException("Cannot sort by '" + order.getProperty()
                        + "'. Sortable fields: " + SORTABLE_FIELDS.stream().sorted().toList());
            }
        });
    }
}