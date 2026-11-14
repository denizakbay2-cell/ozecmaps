package com.citynavigator.routing;

import java.time.Instant;

/**
 * Minimises distance; closed or forbidden segments stay unusable.
 */
final class ShortestWeighting extends TravelWeighting {

    ShortestWeighting(RoadGraph graph, TravelMode mode, TrafficConditions traffic, Instant at,
                      RoadTraffic congestion) {
        super(graph, mode, traffic, at, congestion);
    }

    @Override
    public double cost(int entry) {
        return Double.isInfinite(seconds(entry)) ? Double.POSITIVE_INFINITY : graph.length(RoadGraph.segmentOf(entry));
    }

    @Override
    public double lowerBound(double meters) {
        return meters;
    }
}
