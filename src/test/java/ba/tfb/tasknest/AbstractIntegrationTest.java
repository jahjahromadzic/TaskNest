package ba.tfb.tasknest;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Baza za integracione testove. Podize jedan Postgres kontejner za cijeli suite
 * (singleton obrazac - staticki, startovan jednom, gasi ga Ryuk na kraju), pa
 * testovi ne zavise od rucno pokrenutog docker-compose stacka.
 * <p>
 * Verzija imidza je namjerno ista kao u docker-compose.yml.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    static {
        POSTGRES.start();
    }
}
