package com.citynavigator.routing;

/**
 * Distances and directions on the earth's surface. Haversine is exact enough for a city (well below a metre over
 * a few kilometres); projections onto short segments use a local flat approximation.
 */
public final class GeoMath {

    public static final double EARTH_RADIUS_METERS = 6_371_008.8;
    public static final double METERS_PER_DEGREE = EARTH_RADIUS_METERS * Math.PI / 180;

    private GeoMath() {
    }

    public static double distance(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double sinHalfPhi = Math.sin((phi2 - phi1) / 2);
        double sinHalfLambda = Math.sin(Math.toRadians(lon2 - lon1) / 2);
        double h = sinHalfPhi * sinHalfPhi + Math.cos(phi1) * Math.cos(phi2) * sinHalfLambda * sinHalfLambda;
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    public static double distance(GeoPoint a, GeoPoint b) {
        return distance(a.lat(), a.lon(), b.lat(), b.lon());
    }

    /**
     * Initial compass bearing from the first point towards the second: 0 is north, 90 east, in [0, 360).
     */
    public static double bearing(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double deltaLambda = Math.toRadians(lon2 - lon1);
        double y = Math.sin(deltaLambda) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2) - Math.sin(phi1) * Math.cos(phi2) * Math.cos(deltaLambda);
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }

    public static double bearing(GeoPoint from, GeoPoint to) {
        return bearing(from.lat(), from.lon(), to.lat(), to.lon());
    }

    /**
     * How much the direction changes from one bearing to the next, in (-180, 180]: positive turns right
     * (clockwise), negative turns left.
     */
    public static double turnAngle(double bearingIn, double bearingOut) {
        double delta = (bearingOut - bearingIn) % 360;
        if (delta > 180) {
            delta -= 360;
        } else if (delta <= -180) {
            delta += 360;
        }
        return delta;
    }

    /**
     * Where a point falls on the segment a-b: {@code fraction} 0 is a, 1 is b, and how far the point is from there.
     */
    public static Projection project(double lat, double lon, double aLat, double aLon, double bLat, double bLon) {
        double metersPerDegreeLon = METERS_PER_DEGREE * Math.cos(Math.toRadians(lat));
        double ax = (aLon - lon) * metersPerDegreeLon;
        double ay = (aLat - lat) * METERS_PER_DEGREE;
        double dx = (bLon - aLon) * metersPerDegreeLon;
        double dy = (bLat - aLat) * METERS_PER_DEGREE;
        double lengthSquared = dx * dx + dy * dy;
        double fraction = lengthSquared == 0 ? 0 : Math.clamp(-(ax * dx + ay * dy) / lengthSquared, 0.0, 1.0);
        return new Projection(fraction, Math.hypot(ax + fraction * dx, ay + fraction * dy));
    }

    public static GeoPoint interpolate(double aLat, double aLon, double bLat, double bLon, double fraction) {
        return new GeoPoint(aLat + (bLat - aLat) * fraction, aLon + (bLon - aLon) * fraction);
    }

    /**
     * Result of {@link #project}.
     */
    public record Projection(double fraction, double distanceMeters) {
    }
}
