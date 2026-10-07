package ba.tfb.tasknest.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(
        @NotBlank
        @Size(max = 255)
        String token,

        @NotBlank
        @Size(min = 8, max = 100)
        String password
) {
}
