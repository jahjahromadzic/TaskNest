package ba.tfb.tasknest.dto.admin;

import ba.tfb.tasknest.entity.enums.TaskStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record AdminTaskResponse(
        UUID id,
        String title,
        TaskStatus status,
        String categorySlug,
        String categoryName,
        String municipalityName,
        UUID clientId,
        String clientName,
        String clientEmail,
        BigDecimal budget,
        LocalDateTime createdAt,
        LocalDateTime publishedAt
) {
}
