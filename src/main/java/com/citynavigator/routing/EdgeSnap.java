package com.citynavigator.routing;

/**
 * A coordinate moved onto the nearest road of the {@link NationalGraph}.
 *
 * @param piece          which piece of the edge's shape it landed on (between shape points {@code piece} and
 *                       {@code piece + 1})
 * @param offsetMeters   distance along the edge from its first tower to the snapped point
 * @param point          the coordinate on the road
 * @param distanceMeters how far the original coordinate was from the road
 */
public record EdgeSnap(int edge, int piece, double offsetMeters, GeoPoint point, double distanceMeters) {
}
