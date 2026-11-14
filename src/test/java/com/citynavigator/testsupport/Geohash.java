package com.citynavigator.testsupport;

import java.util.Set;
import java.util.TreeSet;

/**
 * Geohash encoding, to make congestion profiles for places in tests.
 */
public final class Geohash {

    private static final String BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz";

    private Geohash() {
    }

    public static String encode(double lat, double lon) {
        double[] latRange = {-90, 90};
        double[] lonRange = {-180, 180};
        StringBuilder hash = new StringBuilder();
        boolean longitudeBit = true;
        int value = 0;
        int bits = 0;
        while (hash.length() < 6) {
            double[] range = longitudeBit ? lonRange : latRange;
            double coordinate = longitudeBit ? lon : lat;
            double middle = (range[0] + range[1]) / 2;
            value <<= 1;
            if (coordinate >= middle) {
                value |= 1;
                range[0] = middle;
            } else {
                range[1] = middle;
            }
            longitudeBit = !longitudeBit;
            if (++bits == 5) {
                hash.append(BASE32.charAt(value));
                value = 0;
                bits = 0;
            }
        }
        return hash.toString();
    }

    /** The cells covering a rectangle. */
    public static Set<String> cover(double minLat, double minLon, double maxLat, double maxLon) {
        Set<String> cells = new TreeSet<>();
        for (double lat = minLat; lat <= maxLat + 0.002; lat += 0.002) {
            for (double lon = minLon; lon <= maxLon + 0.002; lon += 0.002) {
                cells.add(encode(Math.min(lat, maxLat), Math.min(lon, maxLon)));
            }
        }
        return cells;
    }
}
