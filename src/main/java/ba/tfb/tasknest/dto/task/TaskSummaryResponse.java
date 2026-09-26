package ba.tfb.tasknest.dto.task;

import ba.tfb.tasknest.entity.enums.TaskStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TaskSummaryResponse(
        UUID id,
        String title,
        BigDecimal budget,
        TaskStatus status,
        String categoryName,
        String municipalityName,
        LocalDateTime publishedAt,
        LocalDateTime expiresAt
) {
}