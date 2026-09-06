package ba.tfb.tasknest.dto.offer;

import ba.tfb.tasknest.entity.Offer;
import ba.tfb.tasknest.entity.enums.OfferStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record OfferResponse(
        UUID id,
        UUID taskId,
        String taskTitle,
        UUID taskerId,
        String taskerName,
        BigDecimal price,
        String message,
        OfferStatus status,
        LocalDateTime createdAt
) {
    public static OfferResponse from(Offer offer) {
        return new OfferResponse(
                offer.getId(),
                offer.getTask().getId(),
                offer.getTask().getTitle(),
                offer.getTasker().getId(),
                offer.getTasker().getFirstName() + " " + offer.getTasker().getLastName(),
                offer.getPrice(),
                offer.getMessage(),
                offer.getStatus(),
                offer.getCreatedAt()
        );
    }
}