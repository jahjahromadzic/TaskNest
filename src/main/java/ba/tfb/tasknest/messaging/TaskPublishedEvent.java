package ba.tfb.tasknest.messaging;

import java.util.UUID;

public record TaskPublishedEvent(
        UUID taskId,
        String title,
        UUID categoryId,
        UUID municipalityId,

        UUID clientId
) {
}
