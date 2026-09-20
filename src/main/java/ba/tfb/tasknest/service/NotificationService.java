package ba.tfb.tasknest.service;

import ba.tfb.tasknest.dto.notification.NotificationResponse;
import ba.tfb.tasknest.entity.Notification;
import ba.tfb.tasknest.entity.User;
import ba.tfb.tasknest.entity.enums.NotificationType;
import ba.tfb.tasknest.exception.NotResourceOwnerException;
import ba.tfb.tasknest.exception.ResourceNotFoundException;
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

    /**
     * Upisuje notifikaciju svakom taskeru koji pokriva i kategoriju i opstinu
     * objavljenog oglasa.
     *
     * @return mete kojima je notifikacija upisana, da pozivalac moze poslati i mail
     */
    @Transactional
    public List<TaskerNotificationTarget> notifyTaskersAboutNewTask(TaskPublishedEvent event) {
        List<TaskerNotificationTarget> targets = taskerProfileRepository.findNotificationTargets(
                event.categoryId(), event.municipalityId(), event.clientId());

        if (targets.isEmpty()) {
            log.debug("Nema taskera koji pokrivaju task {}", event.taskId());
            return List.of();
        }

        List<Notification> notifications = targets.stream()
                .map(target -> buildNotification(target, event))
                .toList();

        notificationRepository.saveAll(notifications);
        log.info("Upisano {} notifikacija za task {}", notifications.size(), event.taskId());

        return targets;
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

    /**
     * Oznacava notifikaciju procitanom. Ponovni poziv nad vec procitanom je bez
     * efekta, da klijent koji dvaput klikne ne dobije gresku.
     */
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

        // getReferenceById, ne findById: treba samo strani kljuc, pa nema razloga
        // ucitavati cijelog korisnika iz baze za svaku notifikaciju.
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
