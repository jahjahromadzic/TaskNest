package ba.tfb.tasknest.dto.auth;

import java.util.List;
import java.util.UUID;

public record AuthResponse(
        String token,
        UUID userId,
        String email,
        String fullName,
        List<String> roles
) {
}