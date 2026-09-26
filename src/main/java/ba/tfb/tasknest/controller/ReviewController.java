package ba.tfb.tasknest.controller;

import ba.tfb.tasknest.dto.common.PagedResponse;
import ba.tfb.tasknest.dto.review.CreateReviewRequest;
import ba.tfb.tasknest.dto.review.ReviewResponse;
import ba.tfb.tasknest.security.UserPrincipal;
import ba.tfb.tasknest.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping("/tasks/{taskId}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse create(@PathVariable UUID taskId,
                                 @Valid @RequestBody CreateReviewRequest request,
                                 @AuthenticationPrincipal UserPrincipal principal) {
        return reviewService.createReview(taskId, principal.getId(), request);
    }

    @GetMapping("/users/{userId}/reviews")
    public PagedResponse<ReviewResponse> received(
            @PathVariable UUID userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return PagedResponse.from(reviewService.getReceivedReviews(userId,
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize())));
    }
}
