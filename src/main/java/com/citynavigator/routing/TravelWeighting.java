package com.citynavigator.routing;

import java.time.Instant;

/**
 * Shared part of the weightings: who may use a segment and how long it takes under the current incidents and, for
 * cars, the usual congestion at that place and time.
 */
abstract sealed class TravelWeighting implements Weighting permits FastestWeighting, ShortestWeighting {

    protected final RoadGraph graph;
    private final TravelMode mode;
    private final TrafficConditions traffic;
    private final Instant at;
    private final RoadTraffic congestion;

    TravelWeighting(RoadGraph graph, TravelMode mode, TrafficConditions traffic, Instant at, RoadTraffic congestion) {
        this.graph = graph;
        this.mode = mode;
        this.traffic = traffic;
        this.at = at;
        this.congestion = congestion;
    }

    @Override
    public TravelMode mode() {
        return mode;
    }

    @Override
    public double seconds(int entry) {
        int segment = RoadGraph.segmentOf(entry);
        RoadWay way = graph.way(segment);
        if (!way.allows(mode, RoadGraph.directionOf(entry))) {
            return Double.POSITIVE_INFINITY;
        }
        double factor = traffic.speedFactor(segment, mode, at);
        if (factor <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        if (mode == TravelMode.CAR) {
            int from = graph.from(segment);
            int to = graph.to(segment);
            factor *= congestion.factor((graph.lat(from) + graph.lat(to)) / 2, (graph.lon(from) + graph.lon(to)) / 2);
        }
        return graph.length(segment) / (way.speedKmh(mode) / 3.6 * factor);
    }

    @Override
    public double freeFlowSeconds(int entry) {
        int segment = RoadGraph.segmentOf(entry);
        RoadWay way = graph.way(segment);
        if (!way.allows(mode, RoadGraph.directionOf(entry))) {
            return Double.POSITIVE_INFINITY;
        }
        return graph.length(segment) / (way.speedKmh(mode) / 3.6);
    }
}
