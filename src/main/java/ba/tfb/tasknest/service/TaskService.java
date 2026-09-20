package ba.tfb.tasknest.service;

import ba.tfb.tasknest.domain.TaskStateMachine;
import ba.tfb.tasknest.dto.task.CreateTaskRequest;
import ba.tfb.tasknest.dto.task.TaskResponse;
import ba.tfb.tasknest.dto.task.TaskSummaryResponse;
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
import ba.tfb.tasknest.messaging.TaskPublishedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskService {

    private static final int PUBLICATION_VALIDITY_DAYS = 30;

    /**
     * Polja po kojima je dozvoljeno sortirati listu. Bijela lista, a ne bilo koje
     * ime: Spring Data ubacuje ime u JPQL, pa nepoznato polje daje
     * PropertyReferenceException i 500. Uz to, ovdje su samo polja koja su ili
     * indeksirana ili jeftina za sortiranje - sortiranje po description-u nad
     * velikom tabelom nije nesto sto klijent smije naruciti.
     */
    private static final Set<String> SORTABLE_FIELDS =
            Set.of("publishedAt", "createdAt", "updatedAt", "expiresAt", "budget", "title", "status");

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final MunicipalityRepository municipalityRepository;
    private final OfferService offerService;
    private final ApplicationEventPublisher eventPublisher;

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

        LocalDateTime now = LocalDateTime.now();
        task.setStatus(TaskStatus.PUBLISHED);
        task.setPublishedAt(now);
        task.setExpiresAt(now.plusDays(PUBLICATION_VALIDITY_DAYS));

        // Springov dogadjaj, ne direktno na RabbitMQ: TaskEventPublisher ga hvata
        // tek nakon commita, pa se poruka ne salje za objavu koja se rollbackuje.
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

        // Isti redoslijed kao u acceptOffer: task se upise i flushuje prije ponuda,
        // da sve operacije koje diraju i task i ponude zakljucavaju redove istim
        // redom. Ovdje je tasks i ranije isao prvi, ali samo slucajno - kroz
        // auto-flush koji okine upit u rejectActiveOffers. Ovako je namjerno.
        task.setStatus(TaskStatus.CANCELLED);
        task.setAcceptedOffer(null);
        taskRepository.flush();

        // Otkazivanje je dozvoljeno i iz ASSIGNED i IN_PROGRESS, gdje vec postoji
        // prihvacena ponuda i otvoren razgovor. Bez ovoga bi otkazani task ostavio
        // ponudu u ACCEPTED, acceptedOffer koji na nju pokazuje i razgovor u OPEN.
        offerService.rejectActiveOffers(task);

        return TaskResponse.from(task);
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

    private Task loadOwnedTask(UUID taskId, UUID clientId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

        if (!task.getClient().getId().equals(clientId)) {
            throw new NotResourceOwnerException("Task does not belong to this user");
        }

        return task;
    }

    /**
     * Public listing. Only published tasks are visible to everyone.
     */
    @Transactional(readOnly = true)
    public Page<TaskSummaryResponse> browseTasks(UUID categoryId,
                                                 UUID municipalityId,
                                                 Pageable pageable) {
        requireSortableFields(pageable);
        return taskRepository.findOpenTasks(TaskStatus.PUBLISHED, LocalDateTime.now(),
                categoryId, municipalityId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<TaskSummaryResponse> getMatchingTasks(UUID taskerId, Pageable pageable) {
        requireSortableFields(pageable);
        return taskRepository.findMatchingTasks(
                taskerId, TaskStatus.PUBLISHED, LocalDateTime.now(), pageable);
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

    /**
     * Odbija nepoznato polje za sortiranje prije nego stigne do Spring Date.
     * Bez ovoga ?sort=bilokako daje 500 umjesto 400.
     */
    private void requireSortableFields(Pageable pageable) {
        pageable.getSort().forEach(order -> {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new BusinessRuleException("Cannot sort by '" + order.getProperty()
                        + "'. Sortable fields: " + SORTABLE_FIELDS.stream().sorted().toList());
            }
        });
    }
}