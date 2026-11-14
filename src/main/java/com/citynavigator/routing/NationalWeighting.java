package com.citynavigator.routing;

/**
 * Car costs on the {@link NationalGraph}: free-flow speeds by road class and limit, slowed by congestion where
 * traffic data exists. Shortest mode minimises metres over the same roads.
 */
final class NationalWeighting implements Weighting {

    /** At least every speed the graph can produce, so the A* estimate stays a lower bound. */
    static final double MAX_KMH = 110;

    private final NationalGraph graph;
    private final RoadTraffic traffic;
    private final boolean shortest;

    NationalWeighting(NationalGraph graph, RoadTraffic traffic, boolean shortest) {
        this.graph = graph;
        this.traffic = traffic;
        this.shortest = shortest;
    }

    @Override
    public TravelMode mode() {
        return TravelMode.CAR;
    }

    @Override
    public double cost(int entry) {
        if (!shortest) {
            return seconds(entry);
        }
        return graph.allows(entry, TravelMode.CAR) ? graph.length(RoadGraph.segmentOf(entry)) : Double.POSITIVE_INFINITY;
    }

    @Override
    public double seconds(int entry) {
        if (!graph.allows(entry, TravelMode.CAR)) {
            return Double.POSITIVE_INFINITY;
        }
        int edge = RoadGraph.segmentOf(entry);
        int from = graph.from(edge);
        int to = graph.to(edge);
        double factor = traffic.factor((graph.lat(from) + graph.lat(to)) / 2, (graph.lon(from) + graph.lon(to)) / 2);
        return graph.length(edge) / (graph.carSpeedKmh(edge) / 3.6 * factor);
    }

    @Override
    public double freeFlowSeconds(int entry) {
        if (!graph.allows(entry, TravelMode.CAR)) {
            return Double.POSITIVE_INFINITY;
        }
        int edge = RoadGraph.segmentOf(entry);
        return graph.length(edge) / (graph.carSpeedKmh(edge) / 3.6);
    }

    @Override
    public double lowerBound(double meters) {
        return shortest ? meters : meters / (MAX_KMH / 3.6);
    }
}
