package ba.tfb.tasknest.dto.task;

import ba.tfb.tasknest.entity.Task;
import ba.tfb.tasknest.entity.User;
import ba.tfb.tasknest.entity.enums.TaskStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record TaskResponse(
        UUID id,
        String title,
        String description,
        BigDecimal budget,
        TaskStatus status,
        UUID categoryId,
        String categorySlug,
        String categoryName,
        UUID municipalityId,
        String municipalityName,
        UUID clientId,
        String clientName,
        UUID assignedTaskerId,
        String assignedTaskerName,
        LocalDateTime publishedAt,
        LocalDateTime expiresAt,
        LocalDateTime assignedAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime createdAt,
        List<TaskPhotoResponse> photos
) {
    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getBudget(),
                task.getStatus(),
                task.getCategory().getId(),
                task.getCategory().getSlug(),
                task.getCategory().getName(),
                task.getMunicipality().getId(),
                task.getMunicipality().getName(),
                task.getClient().getId(),
                task.getClient().getFirstName() + " " + task.getClient().getLastName(),
                assignedTasker(task) == null ? null : assignedTasker(task).getId(),
                assignedTasker(task) == null ? null
                        : assignedTasker(task).getFirstName() + " " + assignedTasker(task).getLastName(),
                task.getPublishedAt(),
                task.getExpiresAt(),
                task.getAssignedAt(),
                task.getStartedAt(),
                task.getCompletedAt(),
                task.getCreatedAt(),
                task.getPhotos().stream().map(TaskPhotoResponse::from).toList()
        );
    }

    private static User assignedTasker(Task task) {
        return task.getAcceptedOffer() == null ? null : task.getAcceptedOffer().getTasker();
    }
}
