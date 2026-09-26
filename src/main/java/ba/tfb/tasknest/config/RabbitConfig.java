package ba.tfb.tasknest.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "tasknest.events";
    public static final String TASK_PUBLISHED_QUEUE = "tasknest.task-published";
    public static final String TASK_PUBLISHED_ROUTING_KEY = "task.published";

    public static final String TASK_EXPIRED_QUEUE = "tasknest.task-expired";
    public static final String TASK_EXPIRED_ROUTING_KEY = "task.expired";

    @Bean
    public TopicExchange taskNestExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue taskPublishedQueue() {
        return QueueBuilder.durable(TASK_PUBLISHED_QUEUE).build();
    }

    @Bean
    public Binding taskPublishedBinding(Queue taskPublishedQueue, TopicExchange taskNestExchange) {
        return BindingBuilder.bind(taskPublishedQueue)
                .to(taskNestExchange)
                .with(TASK_PUBLISHED_ROUTING_KEY);
    }

    @Bean
    public Queue taskExpiredQueue() {
        return QueueBuilder.durable(TASK_EXPIRED_QUEUE).build();
    }

    @Bean
    public Binding taskExpiredBinding(Queue taskExpiredQueue, TopicExchange taskNestExchange) {
        return BindingBuilder.bind(taskExpiredQueue)
                .to(taskNestExchange)
                .with(TASK_EXPIRED_ROUTING_KEY);
    }

    @Bean
    public MessageConverter rabbitMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter rabbitMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(rabbitMessageConverter);
        return template;
    }
}
