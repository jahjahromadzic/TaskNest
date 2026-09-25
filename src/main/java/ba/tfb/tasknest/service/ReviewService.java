package ba.tfb.tasknest.service;

import ba.tfb.tasknest.dto.review.CreateReviewRequest;
import ba.tfb.tasknest.dto.review.ReviewResponse;
import ba.tfb.tasknest.entity.Offer;
import ba.tfb.tasknest.entity.Review;
import ba.tfb.tasknest.entity.Task;
import ba.tfb.tasknest.entity.User;
import ba.tfb.tasknest.entity.enums.TaskStatus;
import ba.tfb.tasknest.exception.BusinessRuleException;
import ba.tfb.tasknest.exception.NotResourceOwnerException;
import ba.tfb.tasknest.exception.ResourceNotFoundException;
import ba.tfb.tasknest.repository.ReviewRepository;
import ba.tfb.tasknest.repository.TaskRepository;
import ba.tfb.tasknest.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final TaskerProfileService taskerProfileService;
    private final NotificationService notificationService;

    /**
     * Ostavlja ocjenu na zatvorenom poslu.
     * <p>
     * Dozvoljeno samo nad CLOSED, ne nad COMPLETED: COMPLETED postavlja tasker sam,
     * pa bi mogao prijaviti izmisljen zavrsetak i odmah ocijeniti klijenta.
     */
    @Transactional
    public ReviewResponse createReview(UUID taskId, UUID reviewerId, CreateReviewRequest request) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

        if (task.getStatus() != TaskStatus.CLOSED) {
            throw new BusinessRuleException("Only a closed task can be reviewed");
        }

        User reviewer = userRepository.findById(reviewerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", reviewerId));
        User reviewee = counterpartyOf(task, reviewerId);

        if (reviewRepository.existsByTaskAndReviewer(task, reviewer)) {
            throw new BusinessRuleException("You have already reviewed this task");
        }

        Review review = new Review();
        review.setTask(task);
        review.setReviewer(reviewer);
        review.setReviewee(reviewee);
        review.setRating(request.rating());
        review.setComment(request.comment());

        Review saved = persist(review);

        // Redoslijed: ocjena je flushana prije preracunavanja, da prosjek
        // ukljucuje i nju.
        taskerProfileService.refreshAverageRating(reviewee);
        notificationService.notifyReviewReceived(saved);

        return ReviewResponse.from(saved);
    }

    /**
     * Ocjene koje je korisnik dobio. Javno, jer reputacija koja se ne vidi prije
     * dogovora ne sluzi nicemu.
     */
    @Transactional(readOnly = true)
    public Page<ReviewResponse> getReceivedReviews(UUID userId, Pageable pageable) {
        User reviewee = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        return reviewRepository.findByRevieweeOrderByCreatedAtDesc(reviewee, pageable)
                .map(ReviewResponse::from);
    }

    /**
     * Ko koga ocjenjuje - izvedeno iz posla, nikad iz zahtjeva. Klijent ocjenjuje
     * dodijeljenog taskera i obrnuto; iko treci ne moze ocijeniti nijednog od njih.
     */
    private User counterpartyOf(Task task, UUID reviewerId) {
        User client = task.getClient();
        Offer acceptedOffer = task.getAcceptedOffer();

        if (acceptedOffer == null) {
            throw new IllegalStateException("Closed task " + task.getId() + " has no accepted offer");
        }

        User tasker = acceptedOffer.getTasker();

        if (client.getId().equals(reviewerId)) {
            return tasker;
        }
        if (tasker.getId().equals(reviewerId)) {
            return client;
        }

        throw new NotResourceOwnerException(
                "Only the client and the assigned tasker can review this task");
    }

    /**
     * saveAndFlush, ne save: provjera duplikata iznad je check-then-act i ne stiti
     * od dva istovremena zahtjeva. Prava zastita je uq_reviews_task_reviewer, a bez
     * flusha bi pukla na commitu, izvan ovog catch-a, i vratila 500.
     */
    private Review persist(Review review) {
        try {
            return reviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessRuleException("You have already reviewed this task");
        }
    }
}
