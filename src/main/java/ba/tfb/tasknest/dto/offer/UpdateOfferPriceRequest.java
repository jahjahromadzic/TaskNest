package ba.tfb.tasknest.dto.offer;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateOfferPriceRequest(

        @NotNull
        @DecimalMin(value = "0.0", inclusive = false)
        BigDecimal price
) {
}
