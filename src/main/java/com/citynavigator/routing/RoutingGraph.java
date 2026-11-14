package com.citynavigator.routing;

/**
 * What turn-by-turn directions need to know about a road network, so the same rules apply to the detailed district
 * graph and to the national road graph. Entries are encoded the same way in both: {@code edge << 1 | direction}.
 */
public interface RoutingGraph {

    /**
     * The road an entry runs along.
     */
    RoadWay roadOf(int entry);

    boolean allows(int entry, TravelMode mode);

    int firstEntry(int node);

    int endEntry(int node);

    int entryAt(int index);

    /**
     * Compass direction in which the entry leaves its source node, measured along its first piece of road.
     */
    double initialBearing(int entry);
}
