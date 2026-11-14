package com.citynavigator.testsupport;

import com.citynavigator.routing.GeoPoint;
import com.citynavigator.routing.RoadClass;
import com.citynavigator.routing.RoadGraph;
import com.citynavigator.routing.RoadWay;

/**
 * Small hand-made road networks on a grid of roughly 100 m cells: {@code node(id, row, column)} places a node
 * {@code row} cells north and {@code column} cells east of the origin.
 */
public final class GraphFixture {

    public static final double ORIGIN_LAT = 41.0;
    public static final double ORIGIN_LON = 29.0;
    /** About 100 m north-south and east-west at this latitude. */
    public static final double ROW = 0.0009;
    public static final double COLUMN = 0.00119;

    /** Everyone, both directions. */
    public static final int ALL = 0b1_1111;
    /** Cars and bicycles only along the way; walkers both ways. */
    public static final int ONE_WAY = 0b1_0101;
    public static final int FOOT_ONLY = 0b1_0000;
    public static final int CAR_ONLY = 0b0_0011;

    private final RoadGraph.Builder builder = RoadGraph.builder();
    private long nextWayId = 100;

    public static GeoPoint at(double row, double column) {
        return new GeoPoint(ORIGIN_LAT + row * ROW, ORIGIN_LON + column * COLUMN);
    }

    public GraphFixture node(long id, double row, double column) {
        builder.addNode(id, ORIGIN_LAT + row * ROW, ORIGIN_LON + column * COLUMN);
        return this;
    }

    public GraphFixture way(String name, long... nodes) {
        return way(name, RoadClass.RESIDENTIAL, ALL, nodes);
    }

    public GraphFixture way(String name, RoadClass roadClass, int access, long... nodes) {
        return way(nextWayId++, name, roadClass, false, access, nodes);
    }

    public GraphFixture roundabout(long... nodes) {
        return way(nextWayId++, null, RoadClass.TERTIARY, true, ONE_WAY, nodes);
    }

    public GraphFixture way(long wayId, String name, RoadClass roadClass, boolean roundabout, int access,
                            long... nodes) {
        builder.addWay(new RoadWay(wayId, name, roadClass, 0, roundabout, access), nodes);
        return this;
    }

    public RoadGraph build() {
        return builder.build();
    }
}
