package ba.tfb.tasknest.messaging;

import ba.tfb.tasknest.config.RabbitConfig;
import ba.tfb.tasknest.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Potrosac dogadjaja o objavi oglasa.
 * <p>
 * Radi izvan zahtjeva koji je oglas objavio, pa klijent ne ceka upis
 * notifikacija. Ako obrada pukne, poruka se ne potvrdjuje i RabbitMQ je
 * ponovo isporuci.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaskPublishedListener {

    private final NotificationService notificationService;

    @RabbitListener(queues = RabbitConfig.TASK_PUBLISHED_QUEUE)
    public void onTaskPublished(TaskPublishedEvent event) {
        log.debug("Primljen TaskPublishedEvent za task {}", event.taskId());

        notificationService.notifyTaskersAboutNewTask(event);
    }
}
