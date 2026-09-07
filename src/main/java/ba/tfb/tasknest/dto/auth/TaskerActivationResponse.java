package ba.tfb.tasknest.dto.auth;

import java.util.List;
import java.util.UUID;

/**
 * Potvrda aktivacije Tasker role. Namjerno ne nosi tokene: role se citaju iz
 * baze na svaki zahtjev, pa postojeci access token odmah nosi i novu rolu -
 * izdavanje novog para bi samo gomilalo redove u refresh_tokens.
 */
public record TaskerActivationResponse(
        UUID userId,
        UUID taskerProfileId,
        List<String> roles
) {
}
