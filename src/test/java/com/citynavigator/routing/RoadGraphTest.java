package com.citynavigator.routing;

import static com.citynavigator.testsupport.GraphFixture.ALL;
import static com.citynavigator.testsupport.GraphFixture.ONE_WAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.citynavigator.testsupport.GraphFixture;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoadGraphTest {

    /** A street 1-2-3 going east and a one-way side street 2-4 going north. */
    private final RoadGraph graph = new GraphFixture()
            .node(1, 0, 0).node(2, 0, 1).node(3, 0, 2).node(4, 1, 1)
            .way(7, "Ana Cadde", RoadClass.PRIMARY, false, ALL, 1, 2, 3)
            .way(8, "Yan Sokak", RoadClass.RESIDENTIAL, false, ONE_WAY, 2, 4)
            .build();

    @Test
    void everyPairOfConsecutiveNodesIsASegmentOfAbout100Metres() {
        assertThat(graph.nodeCount()).isEqualTo(4);
        assertThat(graph.wayCount()).isEqualTo(2);
        assertThat(graph.segmentCount()).isEqualTo(3);
        for (int segment = 0; segment < graph.segmentCount(); segment++) {
            assertThat(graph.length(segment)).isCloseTo(100, within(1.0));
        }
    }

    @Test
    void segmentsAreFoundByWayAndPosition() {
        int second = graph.segment(7, 1);
        assertThat(graph.wayId(second)).isEqualTo(7);
        assertThat(graph.sequence(second)).isEqualTo(1);
        assertThat(graph.way(second).name()).isEqualTo("Ana Cadde");
        assertThat(graph.segment(7, 2)).isEqualTo(-1);
        assertThat(graph.segment(7, -1)).isEqualTo(-1);
        assertThat(graph.segment(99, 0)).isEqualTo(-1);
    }

    @Test
    void eachNodeListsTheEntriesLeavingIt() {
        int junction = graph.from(graph.segment(7, 1));
        List<Integer> targets = new ArrayList<>();
        for (int i = graph.firstEntry(junction); i < graph.endEntry(junction); i++) {
            int entry = graph.entryAt(i);
            assertThat(graph.source(entry)).isEqualTo(junction);
            targets.add(graph.target(entry));
        }
        assertThat(targets).hasSize(3);
    }

    @Test
    void entriesKnowTheirDirectionAndWhoMayUseThem() {
        int side = graph.segment(8, 0);
        int north = RoadGraph.entry(side, RoadGraph.FORWARD);
        int south = RoadGraph.reverse(north);
        assertThat(RoadGraph.segmentOf(south)).isEqualTo(side);
        assertThat(RoadGraph.directionOf(south)).isEqualTo(RoadGraph.BACKWARD);
        assertThat(graph.allows(north, TravelMode.CAR)).isTrue();
        assertThat(graph.allows(south, TravelMode.CAR)).isFalse();
        assertThat(graph.allows(south, TravelMode.FOOT)).isTrue();
        assertThat(graph.allowsAny(side, TravelMode.BICYCLE)).isTrue();
    }

    @Test
    void theBoundingBoxCoversAllNodesAndAMargin() {
        double[] bounds = graph.bounds();
        assertThat(bounds[0]).isEqualTo(GraphFixture.ORIGIN_LAT);
        assertThat(bounds[3]).isCloseTo(GraphFixture.ORIGIN_LON + 2 * GraphFixture.COLUMN, within(1e-12));
        assertThat(graph.covers(GraphFixture.at(0.5, 1), 0)).isTrue();
        assertThat(graph.covers(GraphFixture.at(-5, 1), 0)).isFalse();
        assertThat(graph.covers(GraphFixture.at(-5, 1), 600)).isTrue();
        assertThat(RoadGraph.builder().build().covers(GraphFixture.at(0, 0), 1000)).isFalse();
    }

    @Test
    void theBuilderRejectsBrokenInput() {
        RoadWay way = new RoadWay(1, null, RoadClass.RESIDENTIAL, 0, false, ALL);
        assertThatThrownBy(() -> RoadGraph.builder().addNode(1, 0, 0).addNode(1, 0, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("twice");
        assertThatThrownBy(() -> RoadGraph.builder().addNode(1, 0, 0).addWay(way, new long[] {1}))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("at least two");
        assertThatThrownBy(() -> RoadGraph.builder().addNode(1, 0, 0).addWay(way, new long[] {1, 2}))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unknown node 2");
        assertThatThrownBy(() -> RoadGraph.builder().addNode(1, 0, 0).addNode(2, 0, 1)
                .addWay(way, new long[] {1, 1, 2}))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("repeats node 1");
        assertThatThrownBy(() -> RoadGraph.builder().addNode(1, 0, 0).addNode(2, 0, 1)
                .addWay(way, new long[] {1, 2}).addWay(way, new long[] {2, 1}))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("way 1 was added twice");
    }

    @Test
    void theBuilderGrowsBeyondItsInitialCapacity() {
        RoadGraph.Builder builder = RoadGraph.builder();
        for (int i = 0; i < 3_000; i++) {
            builder.addNode(i, 41 + i * 1e-5, 29);
        }
        assertThat(builder.build().nodeCount()).isEqualTo(3_000);
    }

    @Test
    void waySpeedsDependOnModeClassAndLimit() {
        RoadWay primary = new RoadWay(1, "X", RoadClass.PRIMARY, 0, false, ALL);
        RoadWay limited = new RoadWay(2, "Y", RoadClass.PRIMARY, 30, false, ALL);
        RoadWay track = new RoadWay(3, null, RoadClass.TRACK, 0, false, ALL);
        assertThat(primary.speedKmh(TravelMode.CAR)).isEqualTo(40);
        assertThat(limited.speedKmh(TravelMode.CAR)).isEqualTo(30);
        assertThat(limited.speedKmh(TravelMode.FOOT)).isEqualTo(5);
        assertThat(track.speedKmh(TravelMode.CAR)).isEqualTo(10);
        assertThat(primary.sameRoadAs(new RoadWay(9, "X", RoadClass.SECONDARY, 0, false, ALL))).isTrue();
        assertThat(track.sameRoadAs(new RoadWay(4, null, RoadClass.TRACK, 0, false, ALL))).isFalse();
        assertThatThrownBy(() -> new RoadWay(5, null, RoadClass.TRACK, -1, false, ALL))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(RoadClass.fromOsm("residential")).contains(RoadClass.RESIDENTIAL);
        assertThat(RoadClass.fromOsm("proposed")).isEmpty();
        assertThat(RoadClass.fromOsm(null)).isEmpty();
    }
}
