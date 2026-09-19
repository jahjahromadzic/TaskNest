package ba.tfb.tasknest.dto.reference;

import ba.tfb.tasknest.entity.Municipality;

import java.util.UUID;

public record MunicipalityResponse(
        UUID id,
        String name,
        String region
) {
    public static MunicipalityResponse from(Municipality municipality) {
        return new MunicipalityResponse(
                municipality.getId(),
                municipality.getName(),
                municipality.getRegion()
        );
    }
}