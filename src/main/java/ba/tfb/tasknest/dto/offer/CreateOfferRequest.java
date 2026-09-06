package ba.tfb.tasknest.dto.offer;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateOfferRequest(

        @NotNull
        @DecimalMin(value = "0.0", inclusive = false)
        BigDecimal price,

        @Size(max = 1000)
        String message
) {
}