package ba.tfb.tasknest.geo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
        Set<String> street = streetWords(address);
        if (street.isEmpty()) {
            return Optional.empty();
        }

        Optional<Feature> found = search(address + ", " + municipality, null);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        if (found.get().isPrecise()) {
            return found.filter(feature -> feature.isOn(street)).map(Feature::point);
        }
        GeoPoint municipalityCentre = found.get().point();
        return search(address, municipalityCentre)
                .filter(Feature::isPrecise)
                .filter(feature -> feature.isOn(street))
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

    static Set<String> streetWords(String text) {
        return words(text).stream()
                .filter(word -> word.chars().noneMatch(Character::isDigit))
                .filter(word -> !word.equals("bb"))
                .collect(Collectors.toSet());
    }

    private static List<String> words(String text) {
        if (text == null) {
            return List.of();
        }
        String plain = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd');
        return Arrays.stream(plain.split("[^a-z0-9]+"))
                .filter(word -> !word.isEmpty())
                .toList();
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

        boolean isOn(Set<String> street) {
            if (properties == null) {
                return false;
            }
            String foundStreet = properties.street() != null ? properties.street() : properties.name();
            return words(foundStreet).containsAll(street);
        }

        boolean isNearby() {
            return properties != null && properties.distance() != null
                    && properties.distance().compareTo(MAX_DISTANCE_KM) <= 0;
        }
    }

    record Geometry(List<BigDecimal> coordinates) {
    }

    record Properties(String layer, String street, String name, BigDecimal distance) {
    }
}
