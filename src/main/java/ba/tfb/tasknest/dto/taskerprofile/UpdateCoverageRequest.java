package ba.tfb.tasknest.dto.taskerprofile;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public record UpdateCoverageRequest(

        @NotEmpty
        @Size(max = 50)
        Set<UUID> ids
) {
}