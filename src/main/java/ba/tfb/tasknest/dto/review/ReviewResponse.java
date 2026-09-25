package ba.tfb.tasknest.dto.review;

import ba.tfb.tasknest.entity.Review;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReviewResponse(
        UUID id,
        UUID taskId,
        String taskTitle,
        UUID reviewerId,
        String reviewerName,
        UUID revieweeId,
        Integer rating,
        String comment,
        LocalDateTime createdAt
) {
    public static ReviewResponse from(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getTask().getId(),
                review.getTask().getTitle(),
                review.getReviewer().getId(),
                review.getReviewer().getFirstName() + " " + review.getReviewer().getLastName(),
                review.getReviewee().getId(),
                review.getRating(),
                review.getComment(),
                review.getCreatedAt()
        );
    }
}
