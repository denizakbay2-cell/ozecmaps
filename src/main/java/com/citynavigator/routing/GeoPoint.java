package com.citynavigator.routing;

/**
 * A WGS84 coordinate in degrees, as used by OpenStreetMap and the browser's map.
 */
public record GeoPoint(double lat, double lon) {

    public GeoPoint {
        if (!(lat >= -90 && lat <= 90) || !(lon >= -180 && lon <= 180)) {
            throw new IllegalArgumentException("not a valid coordinate: " + lat + ", " + lon);
        }
    }
}
