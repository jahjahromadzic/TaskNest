package ba.tfb.tasknest;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

/**
 * Baza za integracione testove. Podize jedan Postgres i jedan RabbitMQ kontejner
 * za cijeli suite (singleton obrazac - staticki, startovani jednom, gasi ih Ryuk
 * na kraju), pa testovi ne zavise od rucno pokrenutog docker-compose stacka.
 * <p>
 * RabbitMQ je ovdje, a ne samo u messaging testu, jer @RabbitListener pokusava
 * da se poveze cim se kontekst digne - bez brokera bi svaki integracioni test
 * zatrpavao log greskama o neuspjeloj konekciji.
 * <p>
 * Verzije imidza su namjerno iste kao u docker-compose.yml.
 */
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
