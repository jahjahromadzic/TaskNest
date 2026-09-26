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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    private static final UUID TASK_ID = UUID.randomUUID();
    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID TASKER_ID = UUID.randomUUID();
    private static final UUID OUTSIDER_ID = UUID.randomUUID();

    @Mock private ReviewRepository reviewRepository;
    @Mock private TaskRepository taskRepository;
    @Mock private UserRepository userRepository;
    @Mock private TaskerProfileService taskerProfileService;
    @Mock private NotificationService notificationService;

    @InjectMocks private ReviewService reviewService;

    @Nested
    class WhoMayReview {

        @Test
        void createReview_makesTheTaskerTheReviewee_whenTheClientReviews() {
            // Arrange
            Task task = aClosedTask();
            stubLookups(task, CLIENT_ID);

            // Act
            ReviewResponse response = reviewService.createReview(TASK_ID, CLIENT_ID, aRequest(5));

            // Assert
            assertThat(response.reviewerId()).isEqualTo(CLIENT_ID);
            assertThat(response.revieweeId()).isEqualTo(TASKER_ID);
        }

        @Test
        void createReview_makesTheClientTheReviewee_whenTheTaskerReviews() {
            // Arrange
            Task task = aClosedTask();
            stubLookups(task, TASKER_ID);

            // Act
            ReviewResponse response = reviewService.createReview(TASK_ID, TASKER_ID, aRequest(4));

            // Assert
            assertThat(response.reviewerId()).isEqualTo(TASKER_ID);
            assertThat(response.revieweeId()).isEqualTo(CLIENT_ID);
        }

        @Test
        void createReview_throwsNotOwner_whenCallerIsNeitherPartyToTheTask() {
            // Arrange
            Task task = aClosedTask();
            when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
            when(userRepository.findById(OUTSIDER_ID)).thenReturn(Optional.of(aUser(OUTSIDER_ID)));

            // Act + Assert
            assertThatThrownBy(() -> reviewService.createReview(TASK_ID, OUTSIDER_ID, aRequest(1)))
                    .isInstanceOf(NotResourceOwnerException.class);

            // Assert
            verify(reviewRepository, never()).saveAndFlush(any());
        }

        @Test
        void createReview_throwsNotFound_whenTaskDoesNotExist() {
            // Arrange
            when(taskRepository.findById(TASK_ID)).thenReturn(Optional.empty());

            // Act + Assert
            assertThatThrownBy(() -> reviewService.createReview(TASK_ID, CLIENT_ID, aRequest(5)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Task");
        }
    }

    @Nested
    class WhenReviewingIsAllowed {

        @Test
        void createReview_throwsBusinessRule_whenTaskIsOnlyCompleted() {
            // Arrange
            Task task = aTask(TaskStatus.COMPLETED);
            when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

            // Act + Assert
            assertThatThrownBy(() -> reviewService.createReview(TASK_ID, CLIENT_ID, aRequest(5)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("closed");
        }

        @Test
        void createReview_throwsBusinessRule_whenTaskWasCancelled() {
            // Arrange
            Task task = aTask(TaskStatus.CANCELLED);
            when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

            // Act + Assert
            assertThatThrownBy(() -> reviewService.createReview(TASK_ID, CLIENT_ID, aRequest(5)))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        void createReview_throwsBusinessRule_whenCallerAlreadyReviewedTheTask() {
            // Arrange
            Task task = aClosedTask();
            when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
            when(userRepository.findById(CLIENT_ID)).thenReturn(Optional.of(aUser(CLIENT_ID)));
            when(reviewRepository.existsByTaskAndReviewer(any(), any())).thenReturn(true);

            // Act + Assert
            assertThatThrownBy(() -> reviewService.createReview(TASK_ID, CLIENT_ID, aRequest(5)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("already reviewed");
        }

        @Test
        void createReview_translatesConstraintViolation_whenTwoReviewsRaceThroughTheCheck() {
            // Arrange
            Task task = aClosedTask();
            when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
            when(userRepository.findById(CLIENT_ID)).thenReturn(Optional.of(aUser(CLIENT_ID)));
            when(reviewRepository.saveAndFlush(any()))
                    .thenThrow(new DataIntegrityViolationException("uq_reviews_task_reviewer"));

            // Act + Assert
            assertThatThrownBy(() -> reviewService.createReview(TASK_ID, CLIENT_ID, aRequest(5)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("already reviewed");
        }
    }

    @Nested
    class SideEffects {

        @Test
        void createReview_refreshesTheAverageOfTheReviewee_notOfTheReviewer() {
            // Arrange
            Task task = aClosedTask();
            stubLookups(task, CLIENT_ID);

            // Act
            reviewService.createReview(TASK_ID, CLIENT_ID, aRequest(5));

            // Assert
            ArgumentCaptor<User> refreshed = ArgumentCaptor.forClass(User.class);
            verify(taskerProfileService).refreshAverageRating(refreshed.capture());
            assertThat(refreshed.getValue().getId()).isEqualTo(TASKER_ID);
        }

        @Test
        void createReview_notifiesTheReviewee() {
            // Arrange
            Task task = aClosedTask();
            stubLookups(task, TASKER_ID);

            // Act
            reviewService.createReview(TASK_ID, TASKER_ID, aRequest(3));

            // Assert
            ArgumentCaptor<Review> notified = ArgumentCaptor.forClass(Review.class);
            verify(notificationService).notifyReviewReceived(notified.capture());
            assertThat(notified.getValue().getReviewee().getId()).isEqualTo(CLIENT_ID);
        }
    }

    private void stubLookups(Task task, UUID reviewerId) {
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(userRepository.findById(reviewerId)).thenReturn(Optional.of(aUser(reviewerId)));
        when(reviewRepository.saveAndFlush(any(Review.class)))
                .thenAnswer(call -> call.getArgument(0));
    }

    private CreateReviewRequest aRequest(int rating) {
        return new CreateReviewRequest(rating, "Sve po dogovoru");
    }

    private User aUser(UUID id) {
        User user = new User();
        user.setId(id);
        user.setFirstName("Test");
        user.setLastName("User");
        return user;
    }

    private Task aClosedTask() {
        return aTask(TaskStatus.CLOSED);
    }

    private Task aTask(TaskStatus status) {
        Task task = new Task();
        task.setId(TASK_ID);
        task.setTitle("Popravka slavine");
        task.setStatus(status);
        task.setClient(aUser(CLIENT_ID));

        Offer acceptedOffer = new Offer();
        acceptedOffer.setId(UUID.randomUUID());
        acceptedOffer.setTask(task);
        acceptedOffer.setTasker(aUser(TASKER_ID));
        task.setAcceptedOffer(acceptedOffer);

        return task;
    }
}
