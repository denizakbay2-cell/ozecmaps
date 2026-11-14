package com.citynavigator.dto;

/**
 * The road nearest to a point ("which street am I on?").
 *
 * @param street the road's name, {@code null} if the nearest road has none
 */
public record StreetView(String street, double lat, double lon, double distanceMeters) {
}
