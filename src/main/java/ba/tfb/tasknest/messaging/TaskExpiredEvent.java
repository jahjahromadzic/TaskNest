package ba.tfb.tasknest.messaging;

import java.util.UUID;

public record TaskExpiredEvent(
        UUID taskId,
        String title,
        UUID clientId
) {
}
