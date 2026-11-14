package com.citynavigator.dto;

/**
 * Where a requested start or destination was placed on the road network.
 *
 * @param distanceMeters how far the requested point was from that road
 */
public record SnappedPoint(double lat, double lon, String street, double distanceMeters) {
}
