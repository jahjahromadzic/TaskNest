package ba.tfb.tasknest;

import ba.tfb.tasknest.security.AuthThrottle;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    private static final String TRUNCATE = "TRUNCATE TABLE " + String.join(", ",
            "tasks", "task_photos", "tasker_profiles", "tasker_categories", "tasker_municipalities", "users", "user_roles",
            "notifications", "offers", "conversations", "messages", "reviews",
            "refresh_tokens", "verification_tokens") + " CASCADE";

    private static final int ATTEMPTS = 3;

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    @ServiceConnection
    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:4-management");

    static {
        POSTGRES.start();
        RABBITMQ.start();
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthThrottle authThrottle;

    @AfterEach
    protected void truncateApplicationTables() {
        authThrottle.clear();
        for (int attempt = 1; ; attempt++) {
            try {
                jdbcTemplate.execute(TRUNCATE);
                return;
            } catch (PessimisticLockingFailureException e) {
                if (attempt == ATTEMPTS) {
                    throw e;
                }
            }
        }
    }
}
