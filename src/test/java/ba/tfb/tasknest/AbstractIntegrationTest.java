package ba.tfb.tasknest;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    @ServiceConnection
    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:4-management");

    static {
        POSTGRES.start();
        RABBITMQ.start();
    }
}
