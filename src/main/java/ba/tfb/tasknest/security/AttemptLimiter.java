package ba.tfb.tasknest.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class AttemptLimiter {

    private final int maxAttempts;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    public AttemptLimiter(int maxAttempts, Duration window, Clock clock) {
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.clock = clock;
    }

    public Optional<Duration> blockedFor(String key) {
        Instant now = clock.instant();
        Deque<Instant> recent = attempts.computeIfPresent(key, (ignored, deque) -> prune(deque, now));
        if (recent == null || recent.size() < maxAttempts) {
            return Optional.empty();
        }
        return Optional.of(Duration.between(now, recent.peekFirst().plus(window)));
    }

    public void record(String key) {
        Instant now = clock.instant();
        attempts.compute(key, (ignored, deque) -> {
            Deque<Instant> recent = deque == null ? new ArrayDeque<>() : prune(deque, now);
            if (recent == null) {
                recent = new ArrayDeque<>();
            }
            recent.addLast(now);
            return recent;
        });
    }

    public void reset(String key) {
        attempts.remove(key);
    }

    public void purge() {
        Instant now = clock.instant();
        attempts.keySet().forEach(key -> attempts.computeIfPresent(key, (ignored, deque) -> prune(deque, now)));
    }

    public void clear() {
        attempts.clear();
    }

    private Deque<Instant> prune(Deque<Instant> deque, Instant now) {
        Instant cutoff = now.minus(window);
        while (!deque.isEmpty() && !deque.peekFirst().isAfter(cutoff)) {
            deque.pollFirst();
        }
        return deque.isEmpty() ? null : deque;
    }
}
