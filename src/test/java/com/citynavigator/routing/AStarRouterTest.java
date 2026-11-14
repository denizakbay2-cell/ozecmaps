package com.citynavigator.routing;

import static com.citynavigator.testsupport.GraphFixture.ALL;
import static com.citynavigator.testsupport.GraphFixture.ONE_WAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.citynavigator.testsupport.GraphFixture;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

class AStarRouterTest {

    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");

    /**
     * Two ways from 1 to 3: the bottom road (200 m) and a detour over the top (400 m).
     * <pre>
     *   4 ---- 5 ---- 6
     *   |             |
     *   1 ---- 2 ---- 3
     * </pre>
     */
    private static GraphFixture twoRoutes(RoadClass bottom, RoadClass top) {
        return new GraphFixture()
                .node(1, 0, 0).node(2, 0, 1).node(3, 0, 2).node(4, 1, 0).node(5, 1, 1).node(6, 1, 2)
                .way(10, "Alt Yol", bottom, false, ALL, 1, 2, 3)
                .way(11, "Sol", top, false, ALL, 1, 4)
                .way(12, "Üst Yol", top, false, ALL, 4, 5, 6)
                .way(13, "Sağ", top, false, ALL, 6, 3);
    }

    @Test
    void followsAStreetFromTheMiddleOfOneSegmentToTheMiddleOfAnother() {
        RoadGraph graph = twoRoutes(RoadClass.RESIDENTIAL, RoadClass.RESIDENTIAL).build();
        Path path = route(graph, GraphFixture.at(0, 0.25), GraphFixture.at(0, 1.5), TravelMode.CAR,
                Optimization.SHORTEST).orElseThrow();

        assertThat(path.distanceMeters()).isCloseTo(125, within(1.0));
        assertThat(path.pointCount()).isEqualTo(3);
        assertThat(path.pointNode(0)).isEqualTo(-1);
        assertThat(path.pointNode(1)).isEqualTo(graph.to(graph.segment(10, 0)));
        assertThat(path.pointNode(2)).isEqualTo(-1);
        assertThat(path.pieceMeters(0)).isCloseTo(75, within(1.0));
        assertThat(path.metersBetween(1, 2)).isCloseTo(50, within(1.0));
        assertThat(path.cost()).isCloseTo(path.distanceMeters(), within(1e-6));
        // 125 m at 20 km/h
        assertThat(path.durationSeconds()).isCloseTo(22.5, within(0.2));
    }

    @Test
    void staysOnTheSegmentWhenStartAndDestinationShareIt() {
        RoadGraph graph = twoRoutes(RoadClass.RESIDENTIAL, RoadClass.RESIDENTIAL).build();
        Path path = route(graph, GraphFixture.at(0, 0.7), GraphFixture.at(0, 0.2), TravelMode.CAR,
                Optimization.FASTEST).orElseThrow();
        assertThat(path.pieceCount()).isEqualTo(1);
        assertThat(path.distanceMeters()).isCloseTo(50, within(1.0));
        assertThat(path.cost()).isCloseTo(path.durationSeconds(), within(1e-9));
    }

    @Test
    void carsGoRoundTheBlockAgainstAOneWayStreetWhileWalkersDoNot() {
        RoadGraph graph = new GraphFixture()
                .node(1, 0, 0).node(2, 0, 1).node(4, 1, 0).node(5, 1, 1)
                .way(10, "Tek Yön", RoadClass.RESIDENTIAL, false, ONE_WAY, 1, 2)
                .way(11, "Blok", RoadClass.RESIDENTIAL, false, ALL, 2, 5, 4, 1)
                .build();
        GeoPoint from = GraphFixture.at(0, 0.8);
        GeoPoint to = GraphFixture.at(0, 0.2);

        Path byCar = route(graph, from, to, TravelMode.CAR, Optimization.SHORTEST).orElseThrow();
        Path onFoot = route(graph, from, to, TravelMode.FOOT, Optimization.SHORTEST).orElseThrow();

        assertThat(byCar.distanceMeters()).isCloseTo(340, within(2.0));
        assertThat(onFoot.distanceMeters()).isCloseTo(60, within(1.0));
    }

    @Test
    void aClosureSendsCarsAndBicyclesRoundButNotWalkers() {
        RoadGraph graph = twoRoutes(RoadClass.RESIDENTIAL, RoadClass.RESIDENTIAL).build();
        TrafficConditions closed = TrafficConditions.NONE.with(
                TrafficConditions.Restriction.closure(1, null), List.of(graph.segment(10, 0)));
        GeoPoint from = GraphFixture.at(0, 0);
        GeoPoint to = GraphFixture.at(0, 2);

        assertThat(route(graph, from, to, TravelMode.CAR, Optimization.FASTEST, TrafficConditions.NONE)
                .orElseThrow().distanceMeters()).isCloseTo(200, within(1.0));
        assertThat(route(graph, from, to, TravelMode.CAR, Optimization.FASTEST, closed)
                .orElseThrow().distanceMeters()).isCloseTo(400, within(2.0));
        assertThat(route(graph, from, to, TravelMode.BICYCLE, Optimization.SHORTEST, closed)
                .orElseThrow().distanceMeters()).isCloseTo(400, within(2.0));
        assertThat(route(graph, from, to, TravelMode.FOOT, Optimization.SHORTEST, closed)
                .orElseThrow().distanceMeters()).isCloseTo(200, within(1.0));
    }

    @Test
    void aSlowdownMakesTheFastestRouteTakeTheDetourButNotTheShortest() {
        RoadGraph graph = twoRoutes(RoadClass.RESIDENTIAL, RoadClass.RESIDENTIAL).build();
        TrafficConditions jammed = TrafficConditions.NONE.with(TrafficConditions.Restriction.slowdown(1, 0.3, null),
                List.of(graph.segment(10, 0), graph.segment(10, 1)));
        GeoPoint from = GraphFixture.at(0, 0);
        GeoPoint to = GraphFixture.at(0, 2);

        Path fastest = route(graph, from, to, TravelMode.CAR, Optimization.FASTEST, jammed).orElseThrow();
        Path shortest = route(graph, from, to, TravelMode.CAR, Optimization.SHORTEST, jammed).orElseThrow();

        assertThat(fastest.distanceMeters()).isCloseTo(400, within(2.0));
        assertThat(shortest.distanceMeters()).isCloseTo(200, within(1.0));
        // 200 m at 30 % of 20 km/h
        assertThat(shortest.durationSeconds()).isCloseTo(120, within(1.0));
    }

    @Test
    void fastestPrefersTheMainRoadAndShortestTheLane() {
        RoadGraph graph = twoRoutes(RoadClass.LIVING_STREET, RoadClass.PRIMARY).build();
        GeoPoint from = GraphFixture.at(0, 0);
        GeoPoint to = GraphFixture.at(0, 2);

        Path fastest = route(graph, from, to, TravelMode.CAR, Optimization.FASTEST).orElseThrow();
        Path shortest = route(graph, from, to, TravelMode.CAR, Optimization.SHORTEST).orElseThrow();

        assertThat(fastest.distanceMeters()).isCloseTo(400, within(2.0));
        assertThat(fastest.durationSeconds()).isCloseTo(36, within(0.5));
        assertThat(shortest.distanceMeters()).isCloseTo(200, within(1.0));
        assertThat(shortest.durationSeconds()).isCloseTo(72, within(0.5));
    }

    @Test
    void findsNothingBetweenUnconnectedRoads() {
        RoadGraph graph = new GraphFixture()
                .node(1, 0, 0).node(2, 0, 1).node(3, 3, 0).node(4, 3, 1)
                .way("Ada", 1, 2).way("Kıyı", 3, 4)
                .build();
        assertThat(route(graph, GraphFixture.at(0, 0.5), GraphFixture.at(3, 0.5), TravelMode.FOOT,
                Optimization.SHORTEST)).isEmpty();
    }

    @Test
    void theEstimateNeverChangesTheResultOnlyTheWork() {
        Random random = new Random(20261114);
        RoadGraph graph = randomGrid(random, 15);
        SpatialIndex index = new SpatialIndex(graph);
        AStarRouter router = new AStarRouter(graph);
        int astarVisits = 0;
        int dijkstraVisits = 0;
        for (int trial = 0; trial < 300; trial++) {
            TravelMode mode = TravelMode.values()[trial % 3];
            Optimization optimization = Optimization.values()[trial % 2];
            Snap from = index.nearest(GraphFixture.at(random.nextDouble() * 14, random.nextDouble() * 14), 500,
                    segment -> graph.allowsAny(segment, mode)).orElseThrow();
            Snap to = index.nearest(GraphFixture.at(random.nextDouble() * 14, random.nextDouble() * 14), 500,
                    segment -> graph.allowsAny(segment, mode)).orElseThrow();
            Weighting weighting = optimization.weighting(graph, mode, TrafficConditions.NONE, NOW);

            Optional<Path> astar = router.route(from, to, weighting);
            Optional<Path> dijkstra = router.route(from, to, withoutEstimate(weighting));

            assertThat(astar.isPresent()).isEqualTo(dijkstra.isPresent());
            if (astar.isPresent()) {
                assertThat(astar.get().cost()).isCloseTo(dijkstra.get().cost(), within(1e-6));
                astarVisits += astar.get().visitedNodes();
                dijkstraVisits += dijkstra.get().visitedNodes();
            }
        }
        assertThat(astarVisits).isLessThan(dijkstraVisits);
    }

    /** Random streets on an n×n grid: missing links, mixed road classes, one in five one-way. */
    static RoadGraph randomGrid(Random random, int n) {
        GraphFixture fixture = new GraphFixture();
        for (int row = 0; row < n; row++) {
            for (int column = 0; column < n; column++) {
                fixture.node((long) row * n + column, row + random.nextDouble() * 0.3, column + random.nextDouble() * 0.3);
            }
        }
        RoadClass[] classes = {RoadClass.PRIMARY, RoadClass.SECONDARY, RoadClass.RESIDENTIAL,
            RoadClass.LIVING_STREET, RoadClass.FOOTWAY};
        long wayId = 1;
        for (int row = 0; row < n; row++) {
            for (int column = 0; column < n; column++) {
                long node = (long) row * n + column;
                for (long neighbour : new long[] {column + 1 < n ? node + 1 : -1, row + 1 < n ? node + n : -1}) {
                    if (neighbour < 0 || random.nextDouble() < 0.15) {
                        continue;
                    }
                    RoadClass type = classes[random.nextInt(classes.length)];
                    int access = type == RoadClass.FOOTWAY ? 0b1_1100 : random.nextDouble() < 0.2 ? ONE_WAY : ALL;
                    long[] ends = random.nextBoolean() ? new long[] {node, neighbour} : new long[] {neighbour, node};
                    fixture.way(wayId++, null, type, false, access, ends);
                }
            }
        }
        return fixture.build();
    }

    /** The same costs without the A* estimate: plain Dijkstra. */
    private static Weighting withoutEstimate(Weighting weighting) {
        return new Weighting() {
            @Override
            public TravelMode mode() {
                return weighting.mode();
            }

            @Override
            public double cost(int entry) {
                return weighting.cost(entry);
            }

            @Override
            public double seconds(int entry) {
                return weighting.seconds(entry);
            }

            @Override
            public double lowerBound(double meters) {
                return 0;
            }
        };
    }

    private static Optional<Path> route(RoadGraph graph, GeoPoint from, GeoPoint to, TravelMode mode,
                                        Optimization optimization) {
        return route(graph, from, to, mode, optimization, TrafficConditions.NONE);
    }

    private static Optional<Path> route(RoadGraph graph, GeoPoint from, GeoPoint to, TravelMode mode,
                                        Optimization optimization, TrafficConditions traffic) {
        SpatialIndex index = new SpatialIndex(graph);
        Snap start = index.nearest(from, 100, segment -> graph.allowsAny(segment, mode)).orElseThrow();
        Snap goal = index.nearest(to, 100, segment -> graph.allowsAny(segment, mode)).orElseThrow();
        return new AStarRouter(graph).route(start, goal, optimization.weighting(graph, mode, traffic, NOW));
    }
}
