package ba.tfb.tasknest.repository.projection;

import java.util.UUID;

public record TaskerNotificationTarget(
        UUID userId,
        String email,
        String fullName
) {
}
