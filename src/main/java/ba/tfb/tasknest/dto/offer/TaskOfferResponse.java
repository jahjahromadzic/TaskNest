package ba.tfb.tasknest.dto.offer;

import ba.tfb.tasknest.entity.enums.OfferStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TaskOfferResponse(
        UUID id,
        BigDecimal price,
        String message,
        OfferStatus status,
        LocalDateTime createdAt,
        UUID taskerId,
        String taskerName,
        String taskerHeadline,
        Boolean taskerVerified,
        BigDecimal taskerRating,
        Integer taskerCompletedJobs,
        Long taskerReviewCount
) {
}
