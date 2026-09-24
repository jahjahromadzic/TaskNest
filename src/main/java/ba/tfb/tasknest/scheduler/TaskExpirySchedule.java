package ba.tfb.tasknest.scheduler;

import ba.tfb.tasknest.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodicno prebacuje istekle oglase u EXPIRED.
 * <p>
 * Samo okidac - sva logika je u TaskService, da se moze testirati bez cekanja
 * na raspored. Iskljucuje se u testovima, gdje se servis zove direktno.
 */
@Component
@ConditionalOnProperty(name = "app.task-expiry.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class TaskExpirySchedule {

    private final TaskService taskService;

    /**
     * fixedDelay, ne fixedRate: sljedeci prolaz krece tek kad se prethodni zavrsi,
     * pa se ne preklapaju ako obrada potraje.
     */
    @Scheduled(
            fixedDelayString = "${app.task-expiry.interval-ms:60000}",
            initialDelayString = "${app.task-expiry.initial-delay-ms:10000}")
    public void expireOverdueTasks() {
        int expired = taskService.expireOverdueTasks();

        if (expired > 0) {
            log.info("Isteklo {} oglasa", expired);
        }
    }
}
