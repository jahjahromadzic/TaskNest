package ba.tfb.tasknest;

import ba.tfb.tasknest.geo.GeoPoint;
import ba.tfb.tasknest.geo.Geocoder;
import ba.tfb.tasknest.security.AuthThrottle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    private static final String TRUNCATE = "TRUNCATE TABLE " + String.join(", ",
            "tasks", "task_photos", "tasker_profiles", "tasker_categories", "tasker_municipalities", "users", "user_roles",
            "notifications", "offers", "conversations", "messages", "reviews",
            "refresh_tokens", "verification_tokens", "reports") + " CASCADE";

    private static final int ATTEMPTS = 3;

    protected static final GeoPoint SARAJEVO = new GeoPoint(new BigDecimal("43.856430"), new BigDecimal("18.413029"));

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

    @MockitoBean
    protected Geocoder geocoder;

    @BeforeEach
    protected void locateEveryAddressInSarajevo() {
        when(geocoder.geocode(anyString(), anyString())).thenReturn(Optional.of(SARAJEVO));
    }

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
