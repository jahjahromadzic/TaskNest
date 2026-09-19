package ba.tfb.tasknest.dto.taskerprofile;

import jakarta.validation.constraints.Size;

public record UpdateTaskerProfileRequest(

        @Size(max = 150)
        String headline,

        @Size(max = 2000)
        String bio
) {
}