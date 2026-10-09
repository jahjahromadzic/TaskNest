package ba.tfb.tasknest.service;

import ba.tfb.tasknest.domain.TaskStateMachine;
import ba.tfb.tasknest.dto.task.CreateTaskRequest;
import ba.tfb.tasknest.dto.task.TaskResponse;
import ba.tfb.tasknest.dto.task.TaskSummaryResponse;
import ba.tfb.tasknest.dto.task.UpdateTaskRequest;
import ba.tfb.tasknest.entity.Category;
import ba.tfb.tasknest.entity.Municipality;
import ba.tfb.tasknest.entity.Offer;
import ba.tfb.tasknest.entity.Task;
import ba.tfb.tasknest.entity.User;
import ba.tfb.tasknest.entity.enums.TaskStatus;
import ba.tfb.tasknest.exception.BusinessRuleException;
import ba.tfb.tasknest.exception.NotResourceOwnerException;
import ba.tfb.tasknest.exception.ResourceNotFoundException;
import ba.tfb.tasknest.geo.GeoPoint;
import ba.tfb.tasknest.geo.Geocoder;
import ba.tfb.tasknest.repository.CategoryRepository;
import ba.tfb.tasknest.repository.MunicipalityRepository;
import ba.tfb.tasknest.repository.TaskRepository;
import ba.tfb.tasknest.repository.UserRepository;
import ba.tfb.tasknest.messaging.TaskExpiredEvent;
import ba.tfb.tasknest.messaging.TaskPublishedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskService {

    public static final int PUBLICATION_VALIDITY_DAYS = 30;

    private static final Set<String> SORTABLE_FIELDS =
            Set.of("publishedAt", "createdAt", "updatedAt", "expiresAt", "budget", "title", "status");

    private static final Set<TaskStatus> EDITABLE_STATUSES = EnumSet.of(TaskStatus.DRAFT, TaskStatus.PUBLISHED);

    private static final String ACCENTED_LETTERS = "čćšđž";
    private static final String PLAIN_LETTERS = "ccsdz";

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final MunicipalityRepository municipalityRepository;
    private final OfferService offerService;
    private final TaskerProfileService taskerProfileService;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final Geocoder geocoder;

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

        GeoPoint point = locate(request.address(), municipality);

        Task task = new Task();
        task.setClient(client);
        task.setCategory(category);
        task.setMunicipality(municipality);
        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setBudget(request.budget());
        task.setAddressLine(request.address().strip());
        task.setLatitude(point.latitude());
        task.setLongitude(point.longitude());
        task.setStatus(TaskStatus.DRAFT);

        return TaskResponse.from(taskRepository.save(task), clientId);
    }

    @Transactional
    public TaskResponse updateTask(UUID taskId, UUID clientId,  UpdateTaskRequest request) {
        Task task = taskRepository.findWithWriteLockById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

        if (!task.getClient().getId().equals(clientId)) {
            throw new NotResourceOwnerException("Task does not belong to this user");
        }

        if (!EDITABLE_STATUSES.contains(task.getStatus())) {
            throw new BusinessRuleException("Only a draft or a task that is open for offers can be edited");
        }

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", request.categoryId()));

        if (!category.isActive() && !category.equals(task.getCategory())) {
            throw new BusinessRuleException("Category is not active: " + category.getName());
        }

        Municipality municipality = municipalityRepository.findById(request.municipalityId())
                .orElseThrow(() -> new ResourceNotFoundException("Municipality", request.municipalityId()));

        String address = request.address().strip();
        boolean moved = !address.equals(task.getAddressLine())
                || !municipality.equals(task.getMunicipality());

        boolean changed = !request.title().equals(task.getTitle())
                || !Objects.equals(request.description(), task.getDescription())
                || !category.equals(task.getCategory())
                || !sameAmount(request.budget(), task.getBudget())
                || moved;

        if (!changed) {
            return TaskResponse.from(task, clientId);
        }

        if (moved) {
            GeoPoint point = locate(address, municipality);
            task.setAddressLine(address);
            task.setLatitude(point.latitude());
            task.setLongitude(point.longitude());
        }

        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setCategory(category);
        task.setMunicipality(municipality);
        task.setBudget(request.budget());
        taskRepository.flush();

        if (task.getStatus() == TaskStatus.PUBLISHED) {
            notificationService.notifyTaskUpdated(task, offerService.taskersWithPendingOffers(task));
        }

        return TaskResponse.from(task, clientId);
    }

    private static boolean sameAmount(BigDecimal first, BigDecimal second) {
        return first == null || second == null ? first == second : first.compareTo(second) == 0;
    }

    private GeoPoint locate(String address, Municipality municipality) {
        return geocoder.geocode(address.strip(), municipality.getName())
                .orElseThrow(() -> new BusinessRuleException("The address could not be found"));
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

        return TaskResponse.from(task, clientId);
    }

    @Transactional
    public TaskResponse cancelTask(UUID taskId, UUID clientId) {
        Task task = loadOwnedTask(taskId, clientId);

        TaskStateMachine.validateTransition(task.getStatus(), TaskStatus.CANCELLED);

        task.setStatus(TaskStatus.CANCELLED);
        task.setAcceptedOffer(null);
        taskRepository.flush();

        offerService.rejectActiveOffers(task);

        return TaskResponse.from(task, clientId);
    }

    @Transactional
    public TaskResponse reopenTask(UUID taskId, UUID clientId) {
        Task task = loadOwnedTask(taskId, clientId);

        offerService.releaseAssignment(task);

        return TaskResponse.from(task, clientId);
    }

    @Transactional
    public TaskResponse startTask(UUID taskId, UUID taskerId) {
        Task task = loadAssignedTask(taskId, taskerId);

        TaskStateMachine.validateTransition(task.getStatus(), TaskStatus.IN_PROGRESS);

        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setStartedAt(LocalDateTime.now(clock));

        notificationService.notifyTaskStarted(task);

        return TaskResponse.from(task, taskerId);
    }

    @Transactional
    public TaskResponse completeTask(UUID taskId, UUID taskerId) {
        Task task = loadAssignedTask(taskId, taskerId);

        TaskStateMachine.validateTransition(task.getStatus(), TaskStatus.COMPLETED);

        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(LocalDateTime.now(clock));

        notificationService.notifyTaskCompleted(task);

        return TaskResponse.from(task, taskerId);
    }

    @Transactional
    public TaskResponse closeTask(UUID taskId, UUID clientId) {
        Task task = loadOwnedTask(taskId, clientId);

        User tasker = close(task);
        notificationService.notifyTaskClosed(task, tasker);

        return TaskResponse.from(task,clientId);
    }

    @Transactional(readOnly = true)
    public List<UUID> findTasksAssignedBefore(LocalDateTime cutoff) {
        return taskRepository.findIdsAssignedBefore(TaskStatus.ASSIGNED, cutoff);
    }

    @Transactional(readOnly = true)
    public List<UUID> findTasksCompletedBefore(LocalDateTime cutoff) {
        return taskRepository.findIdsCompletedBefore(TaskStatus.COMPLETED, cutoff);
    }

    @Transactional
    public boolean releaseStaleAssignment(UUID taskId, LocalDateTime cutoff) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

        if (task.getStatus() != TaskStatus.ASSIGNED
                || task.getAssignedAt() == null
                || !task.getAssignedAt().isBefore(cutoff)) {
            return false;
        }

        offerService.releaseStaleAssignment(task);
        return true;
    }

    @Transactional
    public boolean autoCloseTask(UUID taskId, LocalDateTime cutoff) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

        if (task.getStatus() != TaskStatus.COMPLETED
                || task.getCompletedAt() == null
                || !task.getCompletedAt().isBefore(cutoff)) {
            return false;
        }

        User tasker = close(task);
        notificationService.notifyTaskAutoClosed(task, tasker);
        return true;
    }

    private User close(Task task) {
        TaskStateMachine.validateTransition(task.getStatus(), TaskStatus.CLOSED);

        Offer acceptedOffer = requireAcceptedOffer(task);
        User tasker = acceptedOffer.getTasker();

        task.setStatus(TaskStatus.CLOSED);
        taskerProfileService.recordCompletedJob(tasker);
        offerService.archiveConversation(acceptedOffer);

        return tasker;
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

        return TaskResponse.from(task, null);
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

        return TaskResponse.from(task, viewerId);
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
                                                 String region,
                                                 UUID municipalityId,
                                                 String search,
                                                 Pageable pageable) {
        return taskRepository.findOpenTasks(TaskStatus.PUBLISHED, LocalDateTime.now(clock),
                categoryId, region == null ? "" : region.strip(), municipalityId, searchPattern(search),
                sortable(pageable));
    }

    static String searchPattern(String search) {
        if (search == null || search.isBlank()) {
            return "";
        }
        String lowerCase = search.strip().toLowerCase(Locale.ROOT);
        StringBuilder pattern = new StringBuilder(lowerCase.length());
        for (char letter : lowerCase.toCharArray()) {
            int accented = ACCENTED_LETTERS.indexOf(letter);
            if (accented >= 0) {
                pattern.append(PLAIN_LETTERS.charAt(accented));
            } else if (letter == '!' || letter == '%' || letter == '_') {
                pattern.append('!').append(letter);
            } else {
                pattern.append(letter);
            }
        }
        return pattern.toString();
    }

    @Transactional(readOnly = true)
    public Page<TaskSummaryResponse> getMatchingTasks(UUID taskerId, Pageable pageable) {
        return taskRepository.findMatchingTasks(
                taskerId, TaskStatus.PUBLISHED, LocalDateTime.now(clock), sortable(pageable));
    }

    @Transactional(readOnly = true)
    public Page<TaskSummaryResponse> getMyTasks(UUID clientId, Set<TaskStatus> statuses, Pageable pageable) {
        Set<TaskStatus> filter = statuses == null || statuses.isEmpty() ? EnumSet.allOf(TaskStatus.class) : statuses;
        return taskRepository.findByClientId(clientId, filter, sortable(pageable));
    }

    @Transactional(readOnly = true)
    public Map<TaskStatus, Long> countMyTasks(UUID clientId) {
        Map<TaskStatus, Long> counts = new EnumMap<>(TaskStatus.class);
        for (TaskStatus status : TaskStatus.values()) {
            counts.put(status, 0L);
        }
        taskRepository.countByStatusForClient(clientId)
                .forEach(row -> counts.put(row.status(), row.count()));
        return counts;
    }

    @Transactional(readOnly = true)
    public Page<TaskSummaryResponse> getAssignedTasks(UUID taskerId, Pageable pageable) {
        return taskRepository.findAssignedToTasker(taskerId, sortable(pageable));
    }

    private Pageable sortable(Pageable pageable) {
        pageable.getSort().forEach(order -> {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new BusinessRuleException("Cannot sort by '" + order.getProperty()
                        + "'. Sortable fields: " + SORTABLE_FIELDS.stream().sorted().toList());
            }
        });
        Sort emptyValuesLast = Sort.by(pageable.getSort().stream().map(Sort.Order::nullsLast).toList());
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), emptyValuesLast);
    }
}