package ba.tfb.tasknest.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public record BecomeTaskerRequest(
        @NotBlank
        @Size(max = 150)
        String headline,
        @Size(max = 2000)
        String bio,
        @NotEmpty
        @Size(max = 50)
        Set<UUID> categoryIds,
        @NotEmpty
        @Size(max = 50)
        Set<UUID> municipalityIds
) {
}
