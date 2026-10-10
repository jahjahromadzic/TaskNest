package ba.tfb.tasknest.dto.task;

import jakarta.validation.constraints.DecimalMax;
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

        @NotBlank
        @Size(max = 200)
        String address,

        @DecimalMin("-90.0")
        @DecimalMax("90.0")
        BigDecimal latitude,

        @DecimalMin("-180.0")
        @DecimalMax("180.0")
        BigDecimal longitude,

        @DecimalMin("0.0")
        BigDecimal budget
) {
}