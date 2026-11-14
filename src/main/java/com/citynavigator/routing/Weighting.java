package com.citynavigator.routing;

/**
 * What a route minimises (the Strategy in A*). Costs are for travelling a whole segment entry; the router scales
 * them for the partial segments at the start and end of a route.
 */
public interface Weighting {

    TravelMode mode();

    /**
     * Cost of travelling the entry, or {@link Double#POSITIVE_INFINITY} if this mode may not use it.
     */
    double cost(int entry);

    /**
     * Travel time of the entry in seconds; finite whenever {@link #cost} is.
     */
    double seconds(int entry);

    /**
     * A cost that is never more than what covering the straight-line distance really costs. This is the A*
     * heuristic; overestimating it would make A* return routes that are not the best.
     */
    double lowerBound(double meters);

    /**
     * Travel time of the entry without congestion, to tell how much traffic adds.
     */
    default double freeFlowSeconds(int entry) {
        return seconds(entry);
    }
}
