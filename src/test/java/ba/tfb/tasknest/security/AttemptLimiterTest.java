package ba.tfb.tasknest.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class AttemptLimiterTest {

    private final MovableClock clock = new MovableClock(Instant.parse("2026-10-07T10:00:00Z"));
    private final AttemptLimiter limiter = new AttemptLimiter(3, Duration.ofMinutes(15), clock);

    @Test
    @DisplayName("A key is blocked once it reaches the limit, and only until its oldest attempt leaves the window")
    void blocksAtTheLimitUntilTheWindowMovesOn() {
        // Arrange
        limiter.record("amra");
        clock.advance(Duration.ofMinutes(5));
        limiter.record("amra");
        assertThat(limiter.blockedFor("amra")).isEmpty();
        limiter.record("amra");

        // Act & Assert
        assertThat(limiter.blockedFor("amra")).contains(Duration.ofMinutes(10));

        clock.advance(Duration.ofMinutes(10));
        assertThat(limiter.blockedFor("amra")).isEmpty();
    }

    @Test
    @DisplayName("Keys are counted separately, and a reset forgets the attempts of one key")
    void keepsKeysApartAndForgetsOnReset() {
        // Arrange
        for (int i = 0; i < 3; i++) {
            limiter.record("amra");
        }
        limiter.record("haris");

        // Act
        limiter.reset("amra");

        // Assert
        assertThat(limiter.blockedFor("amra")).isEmpty();
        assertThat(limiter.blockedFor("haris")).isEmpty();
    }

    @Test
    @DisplayName("Attempts older than the window do not count towards the limit")
    void ignoresAttemptsOutsideTheWindow() {
        // Arrange
        limiter.record("amra");
        limiter.record("amra");
        clock.advance(Duration.ofMinutes(16));

        // Act
        limiter.record("amra");
        limiter.purge();

        // Assert
        assertThat(limiter.blockedFor("amra")).isEmpty();
    }

    private static final class MovableClock extends Clock {

        private Instant now;

        private MovableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
