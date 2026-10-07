package ba.tfb.tasknest.security;

import ba.tfb.tasknest.exception.TooManyAttemptsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

@Component
@Slf4j
public class AuthThrottle {

    private final AttemptLimiter loginsByEmail;
    private final AttemptLimiter loginsByAddress;
    private final AttemptLimiter resetsByEmail;
    private final AttemptLimiter resetsByAddress;

    public AuthThrottle(Clock clock,
                        @Value("${app.auth-throttle.login-attempts-per-email}") int loginAttemptsPerEmail,
                        @Value("${app.auth-throttle.login-attempts-per-address}") int loginAttemptsPerAddress,
                        @Value("${app.auth-throttle.login-window-minutes}") int loginWindowMinutes,
                        @Value("${app.auth-throttle.reset-requests-per-email}") int resetRequestsPerEmail,
                        @Value("${app.auth-throttle.reset-requests-per-address}") int resetRequestsPerAddress,
                        @Value("${app.auth-throttle.reset-window-minutes}") int resetWindowMinutes) {
        Duration loginWindow = Duration.ofMinutes(loginWindowMinutes);
        Duration resetWindow = Duration.ofMinutes(resetWindowMinutes);
        this.loginsByEmail = new AttemptLimiter(loginAttemptsPerEmail, loginWindow, clock);
        this.loginsByAddress = new AttemptLimiter(loginAttemptsPerAddress, loginWindow, clock);
        this.resetsByEmail = new AttemptLimiter(resetRequestsPerEmail, resetWindow, clock);
        this.resetsByAddress = new AttemptLimiter(resetRequestsPerAddress, resetWindow, clock);
    }

    public void ensureLoginAllowed(String email, String address) {
        Optional<Duration> blocked = Stream.of(loginsByEmail.blockedFor(key(email)), loginsByAddress.blockedFor(address))
                .flatMap(Optional::stream)
                .max(Comparator.naturalOrder());

        if (blocked.isPresent()) {
            long minutes = Math.max(1, (blocked.get().toSeconds() + 59) / 60);
            throw new TooManyAttemptsException(
                    "Too many failed login attempts. Try again in " + minutes + " min.", blocked.get());
        }
    }

    public void recordFailedLogin(String email, String address) {
        loginsByEmail.record(key(email));
        loginsByAddress.record(address);
    }

    public void recordSuccessfulLogin(String email) {
        loginsByEmail.reset(key(email));
    }

    public boolean allowResetRequest(String email, String address) {
        if (resetsByEmail.blockedFor(key(email)).isPresent() || resetsByAddress.blockedFor(address).isPresent()) {
            log.info("Password reset request for {} from {} was throttled", key(email), address);
            return false;
        }
        resetsByEmail.record(key(email));
        resetsByAddress.record(address);
        return true;
    }

    @Scheduled(fixedDelayString = "PT10M")
    public void purge() {
        Stream.of(loginsByEmail, loginsByAddress, resetsByEmail, resetsByAddress).forEach(AttemptLimiter::purge);
    }

    public void clear() {
        Stream.of(loginsByEmail, loginsByAddress, resetsByEmail, resetsByAddress).forEach(AttemptLimiter::clear);
    }

    private static String key(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
