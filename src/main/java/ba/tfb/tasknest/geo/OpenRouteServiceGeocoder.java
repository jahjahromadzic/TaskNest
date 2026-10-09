package ba.tfb.tasknest.geo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Component
public class OpenRouteServiceGeocoder implements Geocoder {

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
        SearchResponse response = restClient.get()
                .uri(uri -> uri.path("/search")
                        .queryParam("text", address + ", " + municipality)
                        .queryParam("boundary.country", "BA")
                        .queryParam("size", 1)
                        .build())
                .retrieve()
                .body(SearchResponse.class);

        if (response == null || response.features().isEmpty())
        {
            return Optional.empty();
        }

        List <BigDecimal> coordinates = response.features().getFirst().geometry().coordinates();

        return Optional.of(new GeoPoint(coordinates.get(1), coordinates.get(0)));

    }

    record SearchResponse(List<Feature> features) {
    }

    record Feature(Geometry geometry) {
    }

    record Geometry(List<BigDecimal> coordinates) {
    }


}
