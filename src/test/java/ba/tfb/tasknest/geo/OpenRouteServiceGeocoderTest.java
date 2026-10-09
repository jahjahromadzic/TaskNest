package ba.tfb.tasknest.geo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenRouteServiceGeocoderTest {

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
        ors.expect(requestTo(startsWith("https://api.heigit.org/pelias/v1/search")))
                .andExpect(header("Authorization", "test-key"))
                .andExpect(queryParam("boundary.country", "BA"))
                .andExpect(queryParam("size", "1"))
                .andRespond(withSuccess("""
                        {"features": [{"geometry": {"coordinates": [18.393707, 43.854947]}}]}
                        """, MediaType.APPLICATION_JSON));

        Optional<GeoPoint> point = geocoder.geocode("Zmaja od Bosne 12", "Centar Sarajevo");

        assertThat(point).contains(new GeoPoint(new BigDecimal("43.854947"), new BigDecimal("18.393707")));
        ors.verify();
    }

    @Test
    @DisplayName("Searches for the address together with its municipality")
    void geocode_sendsAddressAndMunicipality() {
        ors.expect(requestTo(startsWith("https://api.heigit.org/pelias/v1/search")))
                .andExpect(queryParam("text", "Titova%205,%20Tuzla"))
                .andRespond(withSuccess("""
                        {"features": [{"geometry": {"coordinates": [18.6763, 44.5384]}}]}
                        """, MediaType.APPLICATION_JSON));

        geocoder.geocode("Titova 5", "Tuzla");

        ors.verify();
    }

    @Test
    @DisplayName("Returns nothing when the address is not found")
    void geocode_returnsEmpty_whenNothingIsFound() {
        ors.expect(requestTo(startsWith("https://api.heigit.org/pelias/v1/search")))
                .andRespond(withSuccess("""
                        {"features": []}
                        """, MediaType.APPLICATION_JSON));

        Optional<GeoPoint> point = geocoder.geocode("asdfgh 99", "Ilidža");

        assertThat(point).isEmpty();
        ors.verify();
    }
}
