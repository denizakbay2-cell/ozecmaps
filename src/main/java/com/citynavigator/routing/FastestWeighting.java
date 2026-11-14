package com.citynavigator.routing;

import java.time.Instant;

/**
 * Minimises travel time.
 */
final class FastestWeighting extends TravelWeighting {

    private final double maxMetersPerSecond;

    FastestWeighting(RoadGraph graph, TravelMode mode, TrafficConditions traffic, Instant at,
                      RoadTraffic congestion) {
        super(graph, mode, traffic, at, congestion);
        this.maxMetersPerSecond = mode.maxSpeedKmh() / 3.6;
    }

    @Override
    public double cost(int entry) {
        return seconds(entry);
    }

    @Override
    public double lowerBound(double meters) {
        return meters / maxMetersPerSecond;
    }
}
