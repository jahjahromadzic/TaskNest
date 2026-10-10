package ba.tfb.tasknest.geo;

import java.math.BigDecimal;

public record GeoPoint(BigDecimal latitude, BigDecimal longitude) {

    private static final double EARTH_RADIUS_KM = 6371;

    public double kilometresTo(GeoPoint other) {
        double latitude1 = Math.toRadians(latitude.doubleValue());
        double latitude2 = Math.toRadians(other.latitude.doubleValue());
        double deltaLatitude = latitude2 - latitude1;
        double deltaLongitude = Math.toRadians(other.longitude.doubleValue() - longitude.doubleValue());
        double a = Math.pow(Math.sin(deltaLatitude / 2), 2)
                + Math.cos(latitude1) * Math.cos(latitude2) * Math.pow(Math.sin(deltaLongitude / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
    }
}
