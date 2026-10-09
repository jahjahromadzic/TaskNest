package ba.tfb.tasknest.geo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParamCount;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenRouteServiceGeocoderTest {

    private static final String SEARCH = "https://api.heigit.org/pelias/v1/search";

    private MockRestServiceServer ors;
    private OpenRouteServiceGeocoder geocoder;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        ors = MockRestServiceServer.bindTo(builder).build();
        geocoder = new OpenRouteServiceGeocoder(builder, "test-key");
    }

    @Test
    @DisplayName("Sends the key, searches only in Bosnia and returns latitude before longitude")
    void geocode_returnsThePoint() {
        ors.expect(requestTo(startsWith(SEARCH)))
                .andExpect(header("Authorization", "test-key"))
                .andExpect(queryParam("boundary.country", "BA"))
                .andExpect(queryParam("size", "1"))
                .andRespond(found("address", 18.393707, 43.854947, null));

        Optional<GeoPoint> point = geocoder.geocode("Zmaja od Bosne 12", "Centar Sarajevo");

        assertThat(point).contains(point("43.854947", "18.393707"));
        ors.verify();
    }

    @Test
    @DisplayName("Searches for the address together with its municipality and stops at a precise answer")
    void geocode_sendsAddressAndMunicipality() {
        ors.expect(requestTo(startsWith(SEARCH)))
                .andExpect(queryParam("text", "Titova%205,%20Tuzla"))
                .andRespond(found("street", 18.6763, 44.5384, null));

        assertThat(geocoder.geocode("Titova 5", "Tuzla")).contains(point("44.5384", "18.6763"));
        ors.verify();
    }

    @Test
    @DisplayName("Returns nothing when the address is not found")
    void geocode_returnsEmpty_whenNothingIsFound() {
        ors.expect(requestTo(startsWith(SEARCH)))
                .andRespond(withSuccess("""
                        {"features": []}
                        """, MediaType.APPLICATION_JSON));

        assertThat(geocoder.geocode("asdfgh 99", "Ilidža")).isEmpty();
        ors.verify();
    }

    @Test
    @DisplayName("When only the municipality is recognised, the street is searched again near its centre")
    void geocode_retriesNearTheMunicipality_whenOnlyTheMunicipalityIsFound() {
        ors.expect(requestTo(startsWith(SEARCH)))
                .andExpect(queryParam("text", "Kolodvorska%2012,%20Novo%20Sarajevo"))
                .andExpect(queryParamCount(3))
                .andRespond(found("county", 18.393866, 43.855579, null));
        ors.expect(requestTo(startsWith(SEARCH)))
                .andExpect(queryParam("text", "Kolodvorska%2012"))
                .andExpect(queryParam("focus.point.lat", "43.855579"))
                .andExpect(queryParam("focus.point.lon", "18.393866"))
                .andRespond(found("address", 18.389188, 43.856426, "0.387"));

        Optional<GeoPoint> point = geocoder.geocode("Kolodvorska 12", "Novo Sarajevo");

        assertThat(point).contains(point("43.856426", "18.389188"));
        ors.verify();
    }

    @Test
    @DisplayName("A street of the same name in another town is not taken for this one")
    void geocode_returnsEmpty_whenTheRetryLandsFarAway() {
        ors.expect(requestTo(startsWith(SEARCH)))
                .andRespond(found("county", 18.393866, 43.855579, null));
        ors.expect(requestTo(startsWith(SEARCH)))
                .andRespond(found("address", 18.645160, 44.456757, "69.867"));

        assertThat(geocoder.geocode("Bosanska 20", "Novo Sarajevo")).isEmpty();
        ors.verify();
    }

    @Test
    @DisplayName("The centre of the municipality alone is never returned as the address")
    void geocode_returnsEmpty_whenTheRetryIsAlsoImprecise() {
        ors.expect(requestTo(startsWith(SEARCH)))
                .andRespond(found("locality", 18.300030, 43.829390, null));
        ors.expect(requestTo(startsWith(SEARCH)))
                .andRespond(found("locality", 18.300030, 43.829390, "0"));

        assertThat(geocoder.geocode("kod velike džamije", "Ilidža")).isEmpty();
        ors.verify();
    }

    private static GeoPoint point(String latitude, String longitude) {
        return new GeoPoint(new BigDecimal(latitude), new BigDecimal(longitude));
    }

    private static ResponseCreator found(String layer, double longitude, double latitude, String distance) {
        String properties = distance == null
                ? "{\"layer\": \"%s\"}".formatted(layer)
                : "{\"layer\": \"%s\", \"distance\": %s}".formatted(layer, distance);
        return withSuccess("""
                {"features": [{"geometry": {"coordinates": [%s, %s]}, "properties": %s}]}
                """.formatted(longitude, latitude, properties), MediaType.APPLICATION_JSON);
    }
}
