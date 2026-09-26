package ba.tfb.tasknest.dto.conversation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(

        @NotBlank
        @Size(max = 5000)
        String content
) {
}
