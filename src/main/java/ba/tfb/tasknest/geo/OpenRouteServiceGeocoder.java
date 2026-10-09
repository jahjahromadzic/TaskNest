package ba.tfb.tasknest.geo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class OpenRouteServiceGeocoder implements Geocoder {

    private static final Set<String> PRECISE_LAYERS = Set.of("address", "street", "venue");
    private static final BigDecimal MAX_DISTANCE_KM = new BigDecimal("20");

    private final RestClient restClient;

    public OpenRouteServiceGeocoder(RestClient.Builder builder,
                                    @Value("${app.geo.ors-api-key}") String apiKey) {
        this.restClient = builder
                .baseUrl("https://api.heigit.org/pelias/v1")
                .defaultHeader("Authorization", apiKey)
                .build();
    }

    @Override
    public Optional<GeoPoint> geocode(String address, String municipality) {
        Optional<Feature> found = search(address + ", " + municipality, null);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        if (found.get().isPrecise()) {
            return Optional.of(found.get().point());
        }
        GeoPoint municipalityCentre = found.get().point();
        return search(address, municipalityCentre)
                .filter(Feature::isPrecise)
                .filter(Feature::isNearby)
                .map(Feature::point);
    }

    private Optional<Feature> search(String text, GeoPoint near) {
        SearchResponse response = restClient.get()
                .uri(uri -> {
                    uri.path("/search")
                            .queryParam("text", text)
                            .queryParam("boundary.country", "BA")
                            .queryParam("size", 1);
                    if (near != null) {
                        uri.queryParam("focus.point.lat", near.latitude())
                                .queryParam("focus.point.lon", near.longitude());
                    }
                    return uri.build();
                })
                .retrieve()
                .body(SearchResponse.class);

        if (response == null || response.features() == null || response.features().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(response.features().getFirst());
    }

    record SearchResponse(List<Feature> features) {
    }

    record Feature(Geometry geometry, Properties properties) {

        GeoPoint point() {
            List<BigDecimal> coordinates = geometry.coordinates();
            return new GeoPoint(coordinates.get(1), coordinates.get(0));
        }

        boolean isPrecise() {
            return properties != null && PRECISE_LAYERS.contains(properties.layer());
        }

        boolean isNearby() {
            return properties != null && properties.distance() != null
                    && properties.distance().compareTo(MAX_DISTANCE_KM) <= 0;
        }
    }

    record Geometry(List<BigDecimal> coordinates) {
    }

    record Properties(String layer, BigDecimal distance) {
    }
}
