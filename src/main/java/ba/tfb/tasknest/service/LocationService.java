package ba.tfb.tasknest.service;

import ba.tfb.tasknest.dto.geo.LocationResponse;
import ba.tfb.tasknest.entity.Municipality;
import ba.tfb.tasknest.exception.ResourceNotFoundException;
import ba.tfb.tasknest.geo.GeoPoint;
import ba.tfb.tasknest.geo.Geocoder;
import ba.tfb.tasknest.repository.MunicipalityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LocationService {

    private final Geocoder geocoder;
    private final MunicipalityRepository municipalityRepository;

    @Transactional(readOnly = true)
    public LocationResponse lookup(String address, UUID municipalityId) {
        Municipality municipality = municipalityRepository.findById(municipalityId)
                .orElseThrow(() -> new ResourceNotFoundException("Municipality", municipalityId));

        if (address == null || address.isBlank()) {
            return centreOf(municipality);
        }

        try {
            Optional<GeoPoint> point = geocoder.geocode(address.strip(), municipality.getName());
            if (point.isPresent()) {
                return new LocationResponse(point.get().latitude(), point.get().longitude(), true);
            }
        } catch (RestClientException e) {
            log.warn("Address lookup failed, falling back to the municipality centre: {}", e.getMessage());
        }

        return centreOf(municipality);
    }

    private static LocationResponse centreOf(Municipality municipality) {
        return new LocationResponse(municipality.getLatitude(), municipality.getLongitude(), false);
    }
}
