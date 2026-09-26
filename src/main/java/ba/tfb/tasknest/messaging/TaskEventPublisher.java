package ba.tfb.tasknest.messaging;

import ba.tfb.tasknest.config.RabbitConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class TaskEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskPublished(TaskPublishedEvent event) {
        log.debug("Sending TaskPublishedEvent for task {}", event.taskId());

        rabbitTemplate.convertAndSend(
                RabbitConfig.EXCHANGE,
                RabbitConfig.TASK_PUBLISHED_ROUTING_KEY,
                event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskExpired(TaskExpiredEvent event) {
        log.debug("Sending TaskExpiredEvent for task {}", event.taskId());

        rabbitTemplate.convertAndSend(
                RabbitConfig.EXCHANGE,
                RabbitConfig.TASK_EXPIRED_ROUTING_KEY,
                event);
    }
}
