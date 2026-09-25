package ba.tfb.tasknest.dto.conversation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Kolona je TEXT i baza nema granicu, pa je granica ovdje - inace bi jedan
 * zahtjev od nekoliko megabajta bio sasvim legitiman.
 */
public record SendMessageRequest(

        @NotBlank
        @Size(max = 5000)
        String content
) {
}
