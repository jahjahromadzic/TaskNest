package ba.tfb.tasknest.exception;

import lombok.Getter;

import java.time.Duration;

@Getter
public class TooManyAttemptsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyAttemptsException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }
}
