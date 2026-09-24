package ba.tfb.tasknest.messaging;

import ba.tfb.tasknest.config.RabbitConfig;
import ba.tfb.tasknest.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Obavjestava vlasnika da mu je oglas istekao.
 * <p>
 * Drugi potrosac na istom exchange-u; razlikuje ih samo routing key.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaskExpiredListener {

    private final NotificationService notificationService;

    @RabbitListener(queues = RabbitConfig.TASK_EXPIRED_QUEUE)
    public void onTaskExpired(TaskExpiredEvent event) {
        log.debug("Primljen TaskExpiredEvent za task {}", event.taskId());

        notificationService.notifyClientAboutExpiredTask(event);
    }
}
