package ba.tfb.tasknest.dto.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;
import java.util.UUID;

public record AuthResponse(
        String token,
        @JsonIgnore String refreshToken,
        long expiresIn,
        UUID userId,
        String email,
        String fullName,
        List<String> roles
) {
}
