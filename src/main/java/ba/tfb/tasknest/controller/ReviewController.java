package ba.tfb.tasknest.controller;

import ba.tfb.tasknest.dto.common.PagedResponse;
import ba.tfb.tasknest.dto.review.CreateReviewRequest;
import ba.tfb.tasknest.dto.review.ReviewResponse;
import ba.tfb.tasknest.dto.review.ReviewedAs;
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

import java.util.List;
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

    @GetMapping("/tasks/{taskId}/reviews")
    public List<ReviewResponse> forTask(@PathVariable UUID taskId) {
        return reviewService.getTaskReviews(taskId);
    }

    @GetMapping("/users/{userId}/reviews")
    public PagedResponse<ReviewResponse> received(
            @PathVariable UUID userId,
            @RequestParam(required = false) ReviewedAs as,
            @PageableDefault(size = 20) Pageable pageable) {
        return PagedResponse.from(reviewService.getReceivedReviews(userId, as,
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize())));
    }
}
