package ba.tfb.tasknest.dto.auth;

import java.util.List;
import java.util.UUID;

public record AuthResponse(
        String token,
        String refreshToken,
        /** Vijek access tokena u sekundama, da klijent zna kad da pozove /refresh. */
        long expiresIn,
        UUID userId,
        String email,
        String fullName,
        List<String> roles
) {
}
