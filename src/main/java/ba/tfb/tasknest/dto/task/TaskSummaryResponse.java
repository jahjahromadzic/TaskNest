package ba.tfb.tasknest.dto.task;

import ba.tfb.tasknest.entity.enums.TaskStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Lightweight view for listings. Built directly by the database through
 * a JPQL constructor expression, so listing a page of tasks stays one
 * query instead of loading entities and walking their lazy relations.
 */
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