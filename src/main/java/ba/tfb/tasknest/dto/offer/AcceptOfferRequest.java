package ba.tfb.tasknest.dto.offer;

import java.math.BigDecimal;

public record AcceptOfferRequest(
        BigDecimal expectedPrice
) {
}
