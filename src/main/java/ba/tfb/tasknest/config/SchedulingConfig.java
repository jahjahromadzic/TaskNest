package ba.tfb.tasknest.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
public class SchedulingConfig {

    /**
     * Sat kao bean, da se "sada" moze injektovati umjesto citati staticki.
     * <p>
     * Kod koji zove LocalDateTime.now() direktno ne moze se testirati bez
     * cekanja stvarnog vremena. S ovim bean-om test moze podmetnuti fiksan
     * trenutak i provjeriti ponasanje na tacnoj granici isteka.
     */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
