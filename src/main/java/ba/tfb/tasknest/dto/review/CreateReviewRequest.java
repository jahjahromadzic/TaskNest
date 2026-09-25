package ba.tfb.tasknest.dto.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Ocjena nosi samo ocjenu i komentar.
 * <p>
 * Koga ocjenjuje se namjerno ne prima: izvodi se iz posla. Da revieweeId dolazi
 * odavde, svako bi mogao poslati jedinicu bilo kome uz poznat ID tudjeg posla.
 */
public record CreateReviewRequest(

        @NotNull
        @Min(1)
        @Max(5)
        Integer rating,

        @Size(max = 1000)
        String comment
) {
}
