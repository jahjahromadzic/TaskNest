package ba.tfb.tasknest.dto.task;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateTaskRequest(

        @NotBlank
        @Size(max = 200)
        String title,

        @Size(max = 5000)
        String description,

        @NotNull
        UUID categoryId,

        @NotNull
        UUID municipalityId,

        @DecimalMin("0.0")
        BigDecimal budget
) {
}