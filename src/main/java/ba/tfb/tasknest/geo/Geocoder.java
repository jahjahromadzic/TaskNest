package ba.tfb.tasknest.geo;

import java.util.Optional;


public interface Geocoder {

    Optional<GeoPoint> geocode(String address, String municipality);
}
