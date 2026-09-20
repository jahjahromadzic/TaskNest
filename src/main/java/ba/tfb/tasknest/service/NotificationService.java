package ba.tfb.tasknest.service;

import ba.tfb.tasknest.entity.Notification;
import ba.tfb.tasknest.entity.enums.NotificationType;
import ba.tfb.tasknest.messaging.TaskPublishedEvent;
import ba.tfb.tasknest.repository.NotificationRepository;
import ba.tfb.tasknest.repository.TaskerProfileRepository;
import ba.tfb.tasknest.repository.UserRepository;
import ba.tfb.tasknest.repository.projection.TaskerNotificationTarget;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
     * @return koliko je notifikacija upisano
     */
    @Transactional
    public int notifyTaskersAboutNewTask(TaskPublishedEvent event) {
        List<TaskerNotificationTarget> targets = taskerProfileRepository.findNotificationTargets(
                event.categoryId(), event.municipalityId(), event.clientId());

        if (targets.isEmpty()) {
            log.debug("Nema taskera koji pokrivaju task {}", event.taskId());
            return 0;
        }

        List<Notification> notifications = targets.stream()
                .map(target -> buildNotification(target, event))
                .toList();

        notificationRepository.saveAll(notifications);
        log.info("Upisano {} notifikacija za task {}", notifications.size(), event.taskId());

        return notifications.size();
    }

    private Notification buildNotification(TaskerNotificationTarget target, TaskPublishedEvent event) {
        Notification notification = new Notification();

        // getReferenceById, ne findById: treba samo strani kljuc, pa nema razloga
        // ucitavati cijelog korisnika iz baze za svaku notifikaciju.
        notification.setRecipient(userRepository.getReferenceById(target.userId()));
        notification.setType(NotificationType.NEW_TASK_IN_AREA);
        notification.setRelatedEntityId(event.taskId());
        notification.setContent("New task in your area: " + event.title());

        return notification;
    }
}
