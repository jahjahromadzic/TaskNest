package ba.tfb.tasknest.dto.task;

import ba.tfb.tasknest.entity.Task;
import ba.tfb.tasknest.entity.enums.TaskStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TaskResponse(
        UUID id,
        String title,
        String description,
        BigDecimal budget,
        TaskStatus status,
        String categoryName,
        String municipalityName,
        String clientName,
        LocalDateTime publishedAt,
        LocalDateTime expiresAt,
        LocalDateTime createdAt
) {
    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getBudget(),
                task.getStatus(),
                task.getCategory().getName(),
                task.getMunicipality().getName(),
                task.getClient().getFirstName() + " " + task.getClient().getLastName(),
                task.getPublishedAt(),
                task.getExpiresAt(),
                task.getCreatedAt()
        );
    }
}