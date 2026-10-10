package ba.tfb.tasknest.dto.geo;

import java.math.BigDecimal;

public record LocationResponse(
        BigDecimal latitude,
        BigDecimal longitude,
        boolean found
) {
}
