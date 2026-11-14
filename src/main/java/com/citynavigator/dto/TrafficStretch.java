package com.citynavigator.dto;

/**
 * A stretch of a route that traffic or an incident slows down: from point {@code from} to point {@code to} of the
 * route's geometry, driven at {@code factor} of the free-flow speed. Stretches split where the factor crosses 60 % or
 * 40 %, the steps of the traffic layer's colours.
 */
public record TrafficStretch(int from, int to, double factor) {
}
