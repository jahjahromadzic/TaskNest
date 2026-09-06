package ba.tfb.tasknest.service;

import ba.tfb.tasknest.domain.TaskStateMachine;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskService {

    private static final int PUBLICATION_VALIDITY_DAYS = 30;

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final MunicipalityRepository municipalityRepository;
    private final OfferService offerService;

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

        return TaskResponse.from(task);
    }

    @Transactional
    public TaskResponse cancelTask(UUID taskId, UUID clientId) {
        Task task = loadOwnedTask(taskId, clientId);

        TaskStateMachine.validateTransition(task.getStatus(), TaskStatus.CANCELLED);
        task.setStatus(TaskStatus.CANCELLED);

        // Otkazivanje je dozvoljeno i iz ASSIGNED i IN_PROGRESS, gdje vec postoji
        // prihvacena ponuda i otvoren razgovor. Bez ovoga bi otkazani task ostavio
        // ponudu u ACCEPTED, acceptedOffer koji na nju pokazuje i razgovor u OPEN.
        offerService.rejectActiveOffers(task);
        task.setAcceptedOffer(null);

        return TaskResponse.from(task);
    }

    @Transactional(readOnly = true)
    public TaskResponse getTask(UUID taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
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
}