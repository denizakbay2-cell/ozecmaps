package com.citynavigator.routing;

import static com.citynavigator.testsupport.GraphFixture.ALL;
import static com.citynavigator.testsupport.GraphFixture.FOOT_ONLY;
import static org.assertj.core.api.Assertions.assertThat;

import com.citynavigator.testsupport.GraphFixture;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class RoadFootprintTest {

    /**
     * "Uzun Cadde" runs east over nodes 1..6 (100 m apart); "Çapraz Sokak" crosses it at node 3, a footpath
     * leaves node 5.
     */
    private final RoadGraph graph = new GraphFixture()
            .node(1, 0, 0).node(2, 0, 1).node(3, 0, 2).node(4, 0, 3).node(5, 0, 4).node(6, 0, 5)
            .node(7, 1, 2).node(8, -1, 2).node(9, 1, 4)
            .way(10, "Uzun Cadde", RoadClass.SECONDARY, false, ALL, 1, 2, 3)
            .way(11, "Uzun Cadde", RoadClass.SECONDARY, false, ALL, 3, 4, 5, 6)
            .way(12, "Çapraz Sokak", RoadClass.RESIDENTIAL, false, ALL, 7, 3, 8)
            .way(13, null, RoadClass.FOOTWAY, false, FOOT_ONLY, 5, 9)
            .build();
    private final SpatialIndex index = new SpatialIndex(graph);

    @Test
    void anAccidentCoversTheSameRoadWithinItsReachOnBothSides() {
        Snap snap = snapAt(0, 2.5);
        Set<String> covered = describe(RoadFootprint.alongRoad(graph, snap, 120, TravelMode.CAR));
        // from the middle of 3-4: 50 m to node 3 and node 4, so one more segment each way, none beyond
        assertThat(covered).containsExactlyInAnyOrder("10#1", "11#0", "11#1");
    }

    @Test
    void aLongerReachFollowsTheRoadAcrossWaysButNotIntoCrossStreets() {
        Set<String> covered = describe(RoadFootprint.alongRoad(graph, snapAt(0, 2.5), 1_000, TravelMode.CAR));
        assertThat(covered).containsExactlyInAnyOrder("10#0", "10#1", "11#0", "11#1", "11#2");
    }

    @Test
    void aClosureShutsTheBlockBetweenTheSurroundingJunctions() {
        // nodes 4 and 5 have only one other car road, so the block runs from junction 3 to the dead end at 6
        Set<String> covered = describe(RoadFootprint.betweenJunctions(graph, snapAt(0, 3.5), 1_000, TravelMode.CAR));
        assertThat(covered).containsExactlyInAnyOrder("11#0", "11#1", "11#2");
    }

    @Test
    void aClosureStopsAtItsMaximumLength() {
        Set<String> covered = describe(RoadFootprint.betweenJunctions(graph, snapAt(0, 3.5), 50, TravelMode.CAR));
        assertThat(covered).containsExactlyInAnyOrder("11#1", "11#2", "11#0");
        Set<String> tight = describe(RoadFootprint.betweenJunctions(graph, snapAt(0, 0.5), 10, TravelMode.CAR));
        assertThat(tight).containsExactlyInAnyOrder("10#0", "10#1");
    }

    private Snap snapAt(double row, double column) {
        return index.nearest(GraphFixture.at(row, column), 50, segment -> graph.allowsAny(segment, TravelMode.CAR))
                .orElseThrow();
    }

    private Set<String> describe(Set<Integer> segments) {
        return segments.stream().map(segment -> graph.wayId(segment) + "#" + graph.sequence(segment))
                .collect(Collectors.toSet());
    }
}
