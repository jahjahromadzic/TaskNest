package ba.tfb.tasknest.messaging;

import java.util.UUID;

/**
 * Oglasu je istekao rok objave i vise ne prima ponude.
 * <p>
 * Nosi vlasnika, jer se obavjestava bas on - za razliku od objave, gdje se
 * obavjestavaju taskeri a vlasnik se iskljucuje.
 */
public record TaskExpiredEvent(
        UUID taskId,
        String title,
        UUID clientId
) {
}
