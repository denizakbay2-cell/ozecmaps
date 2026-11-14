package com.citynavigator.routing;

import static com.citynavigator.testsupport.GraphFixture.ALL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.citynavigator.testsupport.Corridors;
import com.citynavigator.testsupport.GraphFixture;
import com.citynavigator.testsupport.NationalFixture;
import java.time.Instant;
import java.util.BitSet;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AlternativesTest {

    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");

    @Test
    void theOtherCorridorIsTheAlternativeAndIsDescribedWithItsTrueCosts() {
        RoadGraph graph = Corridors.of(RoadClass.PRIMARY, RoadClass.SECONDARY).build();
        Search search = new Search(graph, TravelMode.CAR);

        Path best = search.best();
        Path other = search.alternative(best).orElseThrow();

        assertThat(streets(graph, best)).containsExactly("Giriş", "Kuzey Yolu", "Çıkış");
        assertThat(streets(graph, other)).containsExactly("Giriş", "Güney Yolu", "Çıkış");
        // 883 m at 35 km/h instead of 40 km/h, plus 100 m of residential streets at 20 km/h
        assertThat(best.durationSeconds()).isCloseTo(97.5, within(0.5));
        assertThat(other.durationSeconds()).isCloseTo(108.8, within(0.5));
        assertThat(other.cost()).isCloseTo(other.durationSeconds(), within(1e-6));
        assertThat(other.freeFlowSeconds()).isCloseTo(other.durationSeconds(), within(1e-6));
        assertThat(Alternatives.sharedMeters(other, Alternatives.segmentsOf(best))).isCloseTo(100, within(1.0));
    }

    @Test
    void aMuchSlowerWayIsNoAlternative() {
        // through a living street the other corridor takes over three times as long
        RoadGraph slow = Corridors.of(RoadClass.PRIMARY, RoadClass.LIVING_STREET).build();
        assertThat(new Search(slow, TravelMode.CAR).alternativeToBest()).isEmpty();
        // at 25 km/h only the strong penalty leaves the main corridor, and that way is 49 % slower
        RoadGraph slower = Corridors.of(RoadClass.PRIMARY, RoadClass.UNCLASSIFIED).build();
        assertThat(new Search(slower, TravelMode.CAR).alternativeToBest()).isEmpty();
    }

    @Test
    void aSingleRoadHasNoAlternative() {
        RoadGraph road = new GraphFixture()
                .node(1, 0, 0).node(2, 0, 3).node(3, 0, 6).node(4, 1, 3)
                .way(10, "Tek Yol", RoadClass.PRIMARY, false, ALL, 1, 2, 3)
                .way(11, "Çıkmaz", RoadClass.RESIDENTIAL, false, ALL, 2, 4)
                .build();
        SpatialIndex index = new SpatialIndex(road);
        Snap start = index.nearest(GraphFixture.at(0, 0.5), 50, segment -> true).orElseThrow();
        Snap goal = index.nearest(GraphFixture.at(0, 5.5), 50, segment -> true).orElseThrow();
        Weighting fastest = Optimization.FASTEST.weighting(road, TravelMode.CAR, TrafficConditions.NONE, NOW);
        AStarRouter router = new AStarRouter(road);
        Path best = router.route(start, goal, fastest).orElseThrow();

        assertThat(router.alternative(start, goal, fastest, best)).isEmpty();
    }

    @Test
    void walkersGetAnAlternativeToo() {
        // on foot both corridors are equally fast, so the other one costs nothing extra
        RoadGraph graph = Corridors.of(RoadClass.PRIMARY, RoadClass.SECONDARY).build();
        Search search = new Search(graph, TravelMode.FOOT);

        Path best = search.best();
        Path other = search.alternative(best).orElseThrow();

        assertThat(other.durationSeconds()).isCloseTo(best.durationSeconds(), within(0.5));
        assertThat(streets(graph, other)).doesNotContainAnyElementsOf(
                streets(graph, best).stream().filter(street -> street.endsWith("Yolu")).toList());
    }

    @Test
    void theNationalRouterFindsTheSameAlternative() {
        NationalGraph graph = NationalFixture.of(Corridors.of(RoadClass.PRIMARY, RoadClass.SECONDARY).build());
        EdgeIndex index = new EdgeIndex(graph);
        EdgeSnap start = index.nearest(Corridors.START, 50, edge -> true).orElseThrow();
        EdgeSnap goal = index.nearest(Corridors.DESTINATION, 50, edge -> true).orElseThrow();
        Weighting fastest = Optimization.FASTEST.weighting(graph, RoadTraffic.NONE);
        NationalRouter router = new NationalRouter(graph);

        Path best = router.route(start, goal, fastest).orElseThrow();
        Path other = router.alternative(start, goal, fastest, best).orElseThrow();

        assertThat(nationalStreets(graph, best)).contains("Kuzey Yolu").doesNotContain("Güney Yolu");
        assertThat(nationalStreets(graph, other)).contains("Güney Yolu").doesNotContain("Kuzey Yolu");
        assertThat(other.cost()).isCloseTo(other.durationSeconds(), within(1e-6));
        assertThat(other.durationSeconds()).isGreaterThan(best.durationSeconds());
    }

    @Test
    void thePenaltyRaisesOnlyTheCostOfTheGivenSegmentsInBothDirections() {
        RoadGraph graph = Corridors.of(RoadClass.PRIMARY, RoadClass.SECONDARY).build();
        Weighting fastest = Optimization.FASTEST.weighting(graph, TravelMode.CAR, TrafficConditions.NONE, NOW);
        int north = graph.segment(31, 0);
        int south = graph.segment(32, 0);
        BitSet penalized = new BitSet();
        penalized.set(north);
        Weighting penalty = new PenaltyWeighting(fastest, penalized, 1.5);

        for (int direction : new int[] {RoadGraph.FORWARD, RoadGraph.BACKWARD}) {
            int entry = RoadGraph.entry(north, direction);
            assertThat(penalty.cost(entry)).isCloseTo(1.5 * fastest.cost(entry), within(1e-9));
            assertThat(penalty.seconds(entry)).isEqualTo(fastest.seconds(entry));
            assertThat(penalty.freeFlowSeconds(entry)).isEqualTo(fastest.freeFlowSeconds(entry));
        }
        int other = RoadGraph.entry(south, RoadGraph.FORWARD);
        assertThat(penalty.cost(other)).isEqualTo(fastest.cost(other));
        assertThat(penalty.lowerBound(1_000)).isEqualTo(fastest.lowerBound(1_000));
        assertThat(penalty.mode()).isEqualTo(TravelMode.CAR);
    }

    private static Set<String> streets(RoadGraph graph, Path path) {
        Set<String> names = new LinkedHashSet<>();
        for (int piece = 0; piece < path.pieceCount(); piece++) {
            names.add(graph.way(RoadGraph.segmentOf(path.pieceEntry(piece))).name());
        }
        return names;
    }

    private static Set<String> nationalStreets(NationalGraph graph, Path path) {
        Set<String> names = new LinkedHashSet<>();
        for (int piece = 0; piece < path.pieceCount(); piece++) {
            names.add(graph.name(RoadGraph.segmentOf(path.pieceEntry(piece))));
        }
        return names;
    }

    /** A car or foot search between the corridors' start and destination. */
    private static final class Search {

        private final AStarRouter router;
        private final Snap start;
        private final Snap goal;
        private final Weighting weighting;

        Search(RoadGraph graph, TravelMode mode) {
            SpatialIndex index = new SpatialIndex(graph);
            router = new AStarRouter(graph);
            start = index.nearest(Corridors.START, 50, segment -> graph.allowsAny(segment, mode)).orElseThrow();
            goal = index.nearest(Corridors.DESTINATION, 50, segment -> graph.allowsAny(segment, mode)).orElseThrow();
            weighting = Optimization.FASTEST.weighting(graph, mode, TrafficConditions.NONE, NOW);
        }

        Path best() {
            return router.route(start, goal, weighting).orElseThrow();
        }

        Optional<Path> alternative(Path best) {
            return router.alternative(start, goal, weighting, best);
        }

        Optional<Path> alternativeToBest() {
            return alternative(best());
        }
    }
}
