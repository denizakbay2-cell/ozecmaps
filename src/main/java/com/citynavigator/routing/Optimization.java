package com.citynavigator.routing;

import java.time.Instant;

/**
 * Which weighting a route search uses.
 */
public enum Optimization {

    FASTEST {
        @Override
        public Weighting weighting(RoadGraph graph, TravelMode mode, TrafficConditions traffic, Instant at,
                                   RoadTraffic congestion) {
            return new FastestWeighting(graph, mode, traffic, at, congestion);
        }
    },
    SHORTEST {
        @Override
        public Weighting weighting(RoadGraph graph, TravelMode mode, TrafficConditions traffic, Instant at,
                                   RoadTraffic congestion) {
            return new ShortestWeighting(graph, mode, traffic, at, congestion);
        }
    };

    /**
     * A weighting for the district graph, with incidents and, for cars, congestion.
     */
    public abstract Weighting weighting(RoadGraph graph, TravelMode mode, TrafficConditions traffic, Instant at,
                                        RoadTraffic congestion);

    public Weighting weighting(RoadGraph graph, TravelMode mode, TrafficConditions traffic, Instant at) {
        return weighting(graph, mode, traffic, at, RoadTraffic.NONE);
    }

    /**
     * A car weighting for the national graph, with congestion where traffic data exists.
     */
    public Weighting weighting(NationalGraph graph, RoadTraffic congestion) {
        return new NationalWeighting(graph, congestion, this == SHORTEST);
    }
}
