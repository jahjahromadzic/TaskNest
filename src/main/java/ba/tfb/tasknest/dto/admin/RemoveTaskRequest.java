package ba.tfb.tasknest.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RemoveTaskRequest(

        @NotBlank
        @Size(max = 250)
        String reason
) {
}
