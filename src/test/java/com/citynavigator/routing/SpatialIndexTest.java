package com.citynavigator.routing;

import static com.citynavigator.testsupport.GraphFixture.ALL;
import static com.citynavigator.testsupport.GraphFixture.FOOT_ONLY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.citynavigator.testsupport.GraphFixture;
import org.junit.jupiter.api.Test;

class SpatialIndexTest {

    /** A street along row 0 (cars) and a footpath along row 1. */
    private final RoadGraph graph = new GraphFixture()
            .node(1, 0, 0).node(2, 0, 4).node(3, 1, 0).node(4, 1, 4)
            .way(7, "Cadde", RoadClass.RESIDENTIAL, false, ALL, 1, 2)
            .way(8, null, RoadClass.FOOTWAY, false, FOOT_ONLY, 3, 4)
            .build();
    private final SpatialIndex index = new SpatialIndex(graph);

    @Test
    void snapsToTheClosestPointOfTheNearestRoad() {
        Snap snap = index.nearest(GraphFixture.at(0.8, 1), 500, segment -> true).orElseThrow();
        assertThat(graph.wayId(snap.segment())).isEqualTo(8);
        assertThat(snap.fraction()).isCloseTo(0.25, within(1e-3));
        assertThat(snap.distanceMeters()).isCloseTo(20, within(0.5));
        assertThat(snap.point().lat()).isCloseTo(GraphFixture.at(1, 1).lat(), within(1e-9));
    }

    @Test
    void skipsRoadsTheFilterRejects() {
        Snap snap = index.nearest(GraphFixture.at(0.8, 1), 500,
                segment -> graph.allowsAny(segment, TravelMode.CAR)).orElseThrow();
        assertThat(graph.wayId(snap.segment())).isEqualTo(7);
        assertThat(snap.distanceMeters()).isCloseTo(80, within(1.0));
    }

    @Test
    void findsNothingBeyondTheMaximumDistance() {
        assertThat(index.nearest(GraphFixture.at(-3, 2), 250, segment -> true)).isEmpty();
        assertThat(index.nearest(GraphFixture.at(-3, 2), 350, segment -> true)).isPresent();
        assertThat(index.nearest(new GeoPoint(39.9, 32.8), 300, segment -> true)).isEmpty();
    }

    @Test
    void searchesNeighbouringCellsWhenTheNearestRoadIsInAnotherCell() {
        // 5 rows north of the footpath: several empty cells away
        Snap snap = index.nearest(GraphFixture.at(4, 2), 1_000, segment -> true).orElseThrow();
        assertThat(graph.wayId(snap.segment())).isEqualTo(8);
        assertThat(snap.distanceMeters()).isCloseTo(300, within(2.0));
    }
}
