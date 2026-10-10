package ba.tfb.tasknest.geo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GeoPointTest {

    private static final GeoPoint SARAJEVO = point("43.856430", "18.413029");
    private static final GeoPoint TUZLA = point("44.538000", "18.667000");

    @Test
    @DisplayName("Measures the straight-line distance between two points on the Earth")
    void kilometresTo_measuresTheDistance() {
        assertThat(SARAJEVO.kilometresTo(TUZLA)).isCloseTo(78.0, within(1.5));
        assertThat(TUZLA.kilometresTo(SARAJEVO)).isCloseTo(SARAJEVO.kilometresTo(TUZLA), within(0.001));
        assertThat(SARAJEVO.kilometresTo(SARAJEVO)).isZero();
    }

    private static GeoPoint point(String latitude, String longitude) {
        return new GeoPoint(new BigDecimal(latitude), new BigDecimal(longitude));
    }
}
