package ba.tfb.tasknest.dto.taskerprofile;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

/**
 * Replaces the whole coverage set. Sending an empty set is rejected
 * because a tasker with no coverage can never be matched to a task.
 * <p>
 * @NotEmpty ovdje daje lijepu poruku o gresci na HTTP granici, ali garanciju
 * daje TaskerProfileService - servise ce zvati i pozivaoci mimo kontrolera.
 */
public record UpdateCoverageRequest(

        @NotEmpty
        // Gornja granica da 10.000 UUID-eva ne ode u jedan IN izraz; sifrarnici
        // imaju red velicine desetak unosa, pa je 50 daleko iznad stvarne potrebe.
        @Size(max = 50)
        Set<UUID> ids
) {
}