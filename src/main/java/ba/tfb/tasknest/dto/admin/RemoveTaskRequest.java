package ba.tfb.tasknest.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Razlog je obavezan: vlasnik ga dobija u notifikaciji, a uklanjanje bez
 * objasnjenja izgleda kao greska u aplikaciji.
 * <p>
 * 250 znakova jer notifikacija ima 500: prefiks + naslov (do 200) + razlog
 * moraju stati u kolonu.
 */
public record RemoveTaskRequest(

        @NotBlank
        @Size(max = 250)
        String reason
) {
}
