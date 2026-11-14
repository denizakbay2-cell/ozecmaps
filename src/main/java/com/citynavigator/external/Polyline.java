package com.citynavigator.external;

import com.citynavigator.routing.GeoPoint;
import java.util.ArrayList;
import java.util.List;

/**
 * Decodes the "encoded polyline" format (Google's algorithm) that OSRM uses for route geometry; OSRM's
 * {@code polyline6} variant keeps six decimals instead of five.
 */
public final class Polyline {

    private Polyline() {
    }

    public static List<GeoPoint> decode(String encoded, int precision) {
        double factor = Math.pow(10, precision);
        List<GeoPoint> points = new ArrayList<>();
        Cursor cursor = new Cursor();
        long lat = 0;
        long lon = 0;
        while (cursor.index < encoded.length()) {
            lat += nextValue(encoded, cursor);
            lon += nextValue(encoded, cursor);
            points.add(new GeoPoint(lat / factor, lon / factor));
        }
        return points;
    }

    /** One zig-zag encoded number: 5-bit chunks offset by 63, a set 0x20 bit meaning "more chunks follow". */
    private static long nextValue(String encoded, Cursor cursor) {
        long result = 0;
        int shift = 0;
        int chunk;
        do {
            if (cursor.index >= encoded.length()) {
                throw new IllegalArgumentException("the polyline ends in the middle of a number");
            }
            chunk = encoded.charAt(cursor.index++) - 63;
            result |= (long) (chunk & 0x1f) << shift;
            shift += 5;
        } while (chunk >= 0x20);
        return (result & 1) != 0 ? ~(result >> 1) : result >> 1;
    }

    private static final class Cursor {
        private int index;
    }
}
