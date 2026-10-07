package ba.tfb.tasknest.messaging;

import ba.tfb.tasknest.AbstractIntegrationTest;
import ba.tfb.tasknest.config.RabbitConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DeadLetterIntegrationTest extends AbstractIntegrationTest {

    private static final long WAIT_MS = 15_000;

    @Autowired private RabbitTemplate rabbitTemplate;
    @Autowired private AmqpAdmin amqpAdmin;

    @BeforeEach
    void emptyTheDeadLetterQueues() {
        amqpAdmin.purgeQueue(RabbitConfig.TASK_PUBLISHED_DEAD_LETTER_QUEUE, false);
        amqpAdmin.purgeQueue(RabbitConfig.TASK_EXPIRED_DEAD_LETTER_QUEUE, false);
    }

    @Test
    @DisplayName("A message that keeps failing is retried with a pause and then parked in the dead-letter queue")
    void failingMessageEndsInTheDeadLetterQueue() {
        // Arrange
        TaskPublishedEvent broken = new TaskPublishedEvent(null, "Broken event", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        long sentAt = System.nanoTime();

        // Act
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.TASK_PUBLISHED_ROUTING_KEY, broken);
        Message parked = rabbitTemplate.receive(RabbitConfig.TASK_PUBLISHED_DEAD_LETTER_QUEUE, WAIT_MS);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - sentAt);

        // Assert
        assertThat(parked).isNotNull();
        assertThat(new String(parked.getBody(), StandardCharsets.UTF_8)).contains("\"title\":\"Broken event\"");
        assertThat(elapsed).as("three attempts with 100 ms and 200 ms pauses").isGreaterThanOrEqualTo(Duration.ofMillis(250));
        assertThat(amqpAdmin.getQueueInfo(RabbitConfig.TASK_PUBLISHED_QUEUE).getMessageCount()).isZero();
    }

    @Test
    @DisplayName("A message that cannot even be read goes straight to the dead-letter queue")
    void unreadableMessageEndsInTheDeadLetterQueue() {
        // Arrange
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        Message garbage = MessageBuilder.withBody("{not json".getBytes(StandardCharsets.UTF_8))
                .andProperties(properties)
                .build();

        // Act
        rabbitTemplate.send(RabbitConfig.EXCHANGE, RabbitConfig.TASK_EXPIRED_ROUTING_KEY, garbage);
        Message parked = rabbitTemplate.receive(RabbitConfig.TASK_EXPIRED_DEAD_LETTER_QUEUE, WAIT_MS);

        // Assert
        assertThat(parked).isNotNull();
        assertThat(new String(parked.getBody(), StandardCharsets.UTF_8)).isEqualTo("{not json");
    }
}
