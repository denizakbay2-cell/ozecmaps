package com.citynavigator.routing;

import java.util.BitSet;

/**
 * Makes the roads of an earlier route dearer, so that a new search looks for another way (the penalty method for
 * alternative routes). Only {@link #cost} changes: travel times stay true, and since costs only grow the A*
 * estimate stays a lower bound.
 */
final class PenaltyWeighting implements Weighting {

    private final Weighting base;
    private final BitSet penalized;
    private final double factor;

    /**
     * @param penalizedSegments segments (in both directions) whose cost is multiplied
     * @param factor            more than 1
     */
    PenaltyWeighting(Weighting base, BitSet penalizedSegments, double factor) {
        this.base = base;
        this.penalized = penalizedSegments;
        this.factor = factor;
    }

    @Override
    public TravelMode mode() {
        return base.mode();
    }

    @Override
    public double cost(int entry) {
        double cost = base.cost(entry);
        return penalized.get(RoadGraph.segmentOf(entry)) ? cost * factor : cost;
    }

    @Override
    public double seconds(int entry) {
        return base.seconds(entry);
    }

    @Override
    public double lowerBound(double meters) {
        return base.lowerBound(meters);
    }

    @Override
    public double freeFlowSeconds(int entry) {
        return base.freeFlowSeconds(entry);
    }
}
