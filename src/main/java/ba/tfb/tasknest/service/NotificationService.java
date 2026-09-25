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

    /**
     * Obavjestava vlasnika da mu je oglas istekao. Za razliku od objave, ovdje je
     * primalac tacno jedan - onaj koji je oglas postavio.
     */
    @Transactional
    public void notifyClientAboutExpiredTask(TaskExpiredEvent event) {
        Notification notification = new Notification();
        notification.setRecipient(userReference(event.clientId()));
        notification.setType(NotificationType.TASK_EXPIRED);
        notification.setRelatedEntityId(event.taskId());
        notification.setContent("Your task has expired: " + event.title());

        notificationRepository.save(notification);
        log.info("Upisana notifikacija o isteku taska {}", event.taskId());
    }

    /**
     * Napredak posla: tasker je poceo, tasker je prijavio zavrsetak, klijent je
     * potvrdio. Tri notifikacije, svaka jednom primaocu.
     * <p>
     * Ove se upisuju sinhrono, u istoj transakciji kao i promjena statusa - za
     * razliku od objave i isteka, koji idu preko RabbitMQ-a. Razlika je u tome sto
     * je ovdje primalac jedan i nema slanja maila, pa nema sta da se odvaja od
     * zahtjeva; a atomicnost je prednost: ne postoji stanje u kojem je posao
     * IN_PROGRESS a druga strana nije obavijestena.
     */
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

    /**
     * Obavjestava ocijenjenog da je dobio ocjenu. Sadrzaj ne nosi broj zvjezdica -
     * ocjena se cita na profilu, a notifikacija koja kaze "dobili ste 1/5" bi
     * postojala samo da zaboli.
     */
    @Transactional
    public void notifyReviewReceived(Review review) {
        save(review.getReviewee(), NotificationType.REVIEW_RECEIVED, review.getTask(),
                "You received a review for: " + review.getTask().getTitle());
    }

    /**
     * Obavjestava drugu stranu o novoj poruci - ali najvise jednom po razgovoru
     * dok ta notifikacija ne bude procitana. Razgovor od 50 poruka daje jednu
     * notifikaciju, ne 50; zvonce pokazuje da ima novih poruka, a koliko ih je
     * broji countUnreadForUser.
     * <p>
     * relatedEntityId je ID razgovora, ne poruke: frontend otvara razgovor.
     */
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

    /** Otvoren razgovor gasi i zvonce za njega. */
    @Transactional
    public void clearNewMessageNotifications(Conversation conversation, UUID readerId) {
        notificationRepository.markReadFor(readerId, NotificationType.NEW_MESSAGE, conversation.getId());
    }

    /** Vlasnik saznaje da mu je oglas uklonjen, i zasto. */
    @Transactional
    public void notifyTaskRemoved(Task task, String reason) {
        save(task.getClient(), NotificationType.TASK_REMOVED, task,
                "Your task was removed by a moderator: " + task.getTitle() + ". Reason: " + reason);
    }

    /**
     * Primalac se prima kao ucitan entitet, ne kao ID: pozivalac ga vec ima u
     * istoj transakciji, pa nema potrebe ni za proxyjem ni za novim upitom.
     */
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
