package ba.tfb.tasknest.dto.reference;

import ba.tfb.tasknest.entity.Municipality;

import java.math.BigDecimal;
import java.util.UUID;

public record MunicipalityResponse(
        UUID id,
        String name,
        String region,
        BigDecimal latitude,
        BigDecimal longitude
) {
    public static MunicipalityResponse from(Municipality municipality) {
        return new MunicipalityResponse(
                municipality.getId(),
                municipality.getName(),
                municipality.getRegion(),
                municipality.getLatitude(),
                municipality.getLongitude()
        );
    }
}