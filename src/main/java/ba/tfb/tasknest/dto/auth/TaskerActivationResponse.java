package ba.tfb.tasknest.dto.auth;

import java.util.List;
import java.util.UUID;

public record TaskerActivationResponse(
        UUID userId,
        UUID taskerProfileId,
        List<String> roles
) {
}
