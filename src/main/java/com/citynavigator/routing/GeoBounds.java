package com.citynavigator.routing;

/**
 * A latitude/longitude rectangle.
 */
public record GeoBounds(double minLat, double minLon, double maxLat, double maxLon) {

    /** Turkey, roughly; also catches the neighbouring borderlands, which is fine for a plausibility check. */
    public static final GeoBounds TURKEY = new GeoBounds(35.8, 25.6, 42.2, 44.9);

    public GeoBounds {
        if (!(minLat <= maxLat) || !(minLon <= maxLon)) {
            throw new IllegalArgumentException("the corners of the rectangle are swapped");
        }
    }

    public boolean contains(GeoPoint point) {
        return point.lat() >= minLat && point.lat() <= maxLat && point.lon() >= minLon && point.lon() <= maxLon;
    }
}
