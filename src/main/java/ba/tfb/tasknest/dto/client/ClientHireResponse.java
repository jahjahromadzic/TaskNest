package ba.tfb.tasknest.dto.client;

import ba.tfb.tasknest.entity.enums.TaskStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record ClientHireResponse(
        UUID taskId,
        String title,
        TaskStatus status,
        String categorySlug,
        String categoryName,
        UUID taskerId,
        String taskerName,
        LocalDateTime assignedAt,
        LocalDateTime completedAt
) {
}
