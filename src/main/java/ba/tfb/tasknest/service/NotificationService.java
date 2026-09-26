package ba.tfb.tasknest.service;

import ba.tfb.tasknest.dto.notification.NotificationResponse;
import ba.tfb.tasknest.entity.Conversation;
import ba.tfb.tasknest.entity.Notification;
import ba.tfb.tasknest.entity.Review;
import ba.tfb.tasknest.entity.Task;
import ba.tfb.tasknest.entity.User;
import ba.tfb.tasknest.entity.enums.NotificationType;
import ba.tfb.tasknest.exception.NotResourceOwnerException;
import ba.tfb.tasknest.exception.ResourceNotFoundException;
import ba.tfb.tasknest.messaging.TaskExpiredEvent;
import ba.tfb.tasknest.messaging.TaskPublishedEvent;
import ba.tfb.tasknest.repository.NotificationRepository;
import ba.tfb.tasknest.repository.TaskerProfileRepository;
import ba.tfb.tasknest.repository.UserRepository;
import ba.tfb.tasknest.repository.projection.TaskerNotificationTarget;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final TaskerProfileRepository taskerProfileRepository;
    private final UserRepository userRepository;

    @Transactional
    public List<TaskerNotificationTarget> notifyTaskersAboutNewTask(TaskPublishedEvent event) {
        List<TaskerNotificationTarget> targets = taskerProfileRepository.findNotificationTargets(
                event.categoryId(), event.municipalityId(), event.clientId());

        if (targets.isEmpty()) {
            log.debug("No taskers cover task {}", event.taskId());
            return List.of();
        }

        List<Notification> notifications = targets.stream()
                .map(target -> buildNotification(target, event))
                .toList();

        notificationRepository.saveAll(notifications);
        log.info("Stored {} notifications for task {}", notifications.size(), event.taskId());

        return targets;
    }

    @Transactional
    public void notifyClientAboutExpiredTask(TaskExpiredEvent event) {
        Notification notification = new Notification();
        notification.setRecipient(userReference(event.clientId()));
        notification.setType(NotificationType.TASK_EXPIRED);
        notification.setRelatedEntityId(event.taskId());
        notification.setContent("Your task has expired: " + event.title());

        notificationRepository.save(notification);
        log.info("Stored expiry notification for task {}", event.taskId());
    }

    @Transactional
    public void notifyTaskStarted(Task task) {
        save(task.getClient(), NotificationType.TASK_STARTED, task,
                "Work has started on your task: " + task.getTitle());
    }

    @Transactional
    public void notifyTaskCompleted(Task task) {
        save(task.getClient(), NotificationType.TASK_COMPLETED, task,
                "Work has been completed on your task: " + task.getTitle());
    }

    @Transactional
    public void notifyTaskClosed(Task task, User tasker) {
        save(tasker, NotificationType.TASK_CLOSED, task,
                "The client closed the task: " + task.getTitle());
    }

    @Transactional
    public void notifyReviewReceived(Review review) {
        save(review.getReviewee(), NotificationType.REVIEW_RECEIVED, review.getTask(),
                "You received a review for: " + review.getTask().getTitle());
    }

    @Transactional
    public void notifyNewMessage(Conversation conversation, User recipient) {
        boolean alreadyPending = notificationRepository
                .existsByRecipientAndTypeAndRelatedEntityIdAndReadFalse(
                        recipient, NotificationType.NEW_MESSAGE, conversation.getId());

        if (alreadyPending) {
            return;
        }

        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setType(NotificationType.NEW_MESSAGE);
        notification.setRelatedEntityId(conversation.getId());
        notification.setContent("New message about: " + conversation.getOffer().getTask().getTitle());

        notificationRepository.save(notification);
    }

    @Transactional
    public void clearNewMessageNotifications(Conversation conversation, UUID readerId) {
        notificationRepository.markReadFor(readerId, NotificationType.NEW_MESSAGE, conversation.getId());
    }

    @Transactional
    public void notifyTaskRemoved(Task task, String reason) {
        save(task.getClient(), NotificationType.TASK_REMOVED, task,
                "Your task was removed by a moderator: " + task.getTitle() + ". Reason: " + reason);
    }

    private void save(User recipient, NotificationType type, Task task, String content) {
        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setType(type);
        notification.setRelatedEntityId(task.getId());
        notification.setContent(content);

        notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getMyNotifications(UUID userId, Pageable pageable) {
        return notificationRepository
                .findByRecipientOrderByCreatedAtDesc(userReference(userId), pageable)
                .map(NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public long countUnread(UUID userId) {
        return notificationRepository.countByRecipientAndReadFalse(userReference(userId));
    }

    @Transactional
    public NotificationResponse markAsRead(UUID notificationId, UUID userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId));

        if (!notification.getRecipient().getId().equals(userId)) {
            throw new NotResourceOwnerException("Notification does not belong to this user");
        }

        notification.setRead(true);

        return NotificationResponse.from(notification);
    }

    private Notification buildNotification(TaskerNotificationTarget target, TaskPublishedEvent event) {
        Notification notification = new Notification();

        notification.setRecipient(userReference(target.userId()));
        notification.setType(NotificationType.NEW_TASK_IN_AREA);
        notification.setRelatedEntityId(event.taskId());
        notification.setContent("New task in your area: " + event.title());

        return notification;
    }

    private User userReference(UUID userId) {
        return userRepository.getReferenceById(userId);
    }
}
