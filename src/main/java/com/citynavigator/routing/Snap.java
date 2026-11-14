package com.citynavigator.routing;

/**
 * A coordinate moved onto the nearest usable road.
 *
 * @param segment        the segment it landed on
 * @param fraction       position along the segment: 0 at its first node, 1 at its second
 * @param point          the coordinate on the road
 * @param distanceMeters how far the original coordinate was from the road
 */
public record Snap(int segment, double fraction, GeoPoint point, double distanceMeters) {
}
