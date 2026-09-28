package ba.tfb.tasknest.dto.client;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ClientProfileResponse(
        UUID userId,
        String fullName,
        LocalDateTime memberSince,
        BigDecimal averageRating,
        long reviewCount,
        long postedTasksCount,
        long hiresCount,
        long completedJobsCount,
        long cancelledTasksCount
) {
}
