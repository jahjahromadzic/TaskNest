package ba.tfb.tasknest.messaging;

import ba.tfb.tasknest.config.RabbitConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Salje dogadjaj na RabbitMQ tek nakon sto se transakcija commita.
 * <p>
 * AFTER_COMMIT je ovdje sustinski: kad bi se poruka slala unutar transakcije,
 * potrosac bi je mogao pokupiti prije nego je task upisan i ne bi ga nasao u
 * bazi. Isto tako, ako se transakcija rollbackuje, poruka se uopste ne salje -
 * nema notifikacija o oglasu koji nije objavljen.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaskEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskPublished(TaskPublishedEvent event) {
        log.debug("Saljem TaskPublishedEvent za task {}", event.taskId());

        rabbitTemplate.convertAndSend(
                RabbitConfig.EXCHANGE,
                RabbitConfig.TASK_PUBLISHED_ROUTING_KEY,
                event);
    }
}
