package com.citynavigator.routing;

import static com.citynavigator.testsupport.GraphFixture.ALL;
import static com.citynavigator.testsupport.GraphFixture.CAR_ONLY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.citynavigator.testsupport.Geohash;
import com.citynavigator.testsupport.GraphFixture;
import com.citynavigator.testsupport.KadikoyNetwork;
import com.citynavigator.testsupport.NationalFixture;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

class NationalRouterTest {

    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");
    /** Cars only along the way. */
    private static final int ONE_WAY_CAR = 0b0_0001;

    /**
     * A block with a one-way street on its south side, and an island road nobody can reach.
     * <pre>
     *   1 --------- 2        Kuzey Sokak
     *   |           |        Batı Sokak, Doğu Sokak
     *   4 ---6---- 3         Güney Sokak, one-way east (6 only shapes it)
     *
     *   8 --- 9              Ada Yolu, not connected
     * </pre>
     */
    private static RoadGraph block() {
        return new GraphFixture()
                .node(1, 1, 0).node(2, 1, 2).node(3, 0, 2).node(4, 0, 0).node(6, 0, 1)
                .node(8, -10, 0).node(9, -10, 1)
                .way(10, "Kuzey Sokak", RoadClass.RESIDENTIAL, false, ALL, 1, 2)
                .way(11, "Batı Sokak", RoadClass.RESIDENTIAL, false, ALL, 1, 4)
                .way(12, "Doğu Sokak", RoadClass.RESIDENTIAL, false, ALL, 2, 3)
                .way(13, "Güney Sokak", RoadClass.RESIDENTIAL, false, ONE_WAY_CAR, 4, 6, 3)
                .way(14, "Ada Yolu", RoadClass.RESIDENTIAL, false, CAR_ONLY, 8, 9)
                .build();
    }

    @Test
    void followsAOneWayStreetAndGoesRoundTheBlockToGetBehindTheStart() {
        NationalGraph graph = NationalFixture.of(block());
        NationalRouter router = new NationalRouter(graph);
        EdgeIndex index = new EdgeIndex(graph);
        Weighting shortest = Optimization.SHORTEST.weighting(graph, RoadTraffic.NONE);
        EdgeSnap west = snap(index, GraphFixture.at(0, 0.5));
        EdgeSnap east = snap(index, GraphFixture.at(0, 1.5));

        Path along = router.route(west, east, shortest).orElseThrow();
        Path around = router.route(east, west, shortest).orElseThrow();

        assertThat(west.edge()).isEqualTo(east.edge());
        assertThat(along.distanceMeters()).isCloseTo(GeoMath.distance(west.point(), east.point()), within(0.5));
        assertThat(along.points()).anySatisfy(point ->
                assertThat(GeoMath.distance(point, GraphFixture.at(0, 1))).isLessThan(0.01));
        assertThat(around.distanceMeters()).isGreaterThan(450).isLessThan(520);
        assertThat(GeoMath.distance(around.points().getFirst(), east.point())).isLessThan(0.01);
        assertThat(GeoMath.distance(around.points().getLast(), west.point())).isLessThan(0.01);
    }

    @Test
    void measuresTheSameRoutesAsTheDetailedEngineOnTheSameRoads() {
        RoadGraph detailed = block();
        NationalGraph graph = NationalFixture.of(detailed);
        GeoPoint[] points = {GraphFixture.at(0, 0.5), GraphFixture.at(0, 1.5), GraphFixture.at(1, 1),
                GraphFixture.at(0.5, 2), GraphFixture.at(0.5, 0)};
        for (GeoPoint from : points) {
            for (GeoPoint to : points) {
                if (from != to) {
                    assertThat(nationalShortest(graph, from, to)).as("%s -> %s", from, to)
                            .isCloseTo(detailedShortest(detailed, from, to), within(0.5));
                }
            }
        }
    }

    @Test
    void findsNoRouteToAnIsland() {
        NationalGraph graph = NationalFixture.of(block());
        EdgeIndex index = new EdgeIndex(graph);

        Optional<Path> path = new NationalRouter(graph).route(snap(index, GraphFixture.at(1, 1)),
                snap(index, GraphFixture.at(-10, 0.5)), Optimization.FASTEST.weighting(graph, RoadTraffic.NONE));

        assertThat(path).isEmpty();
    }

    @Test
    void givesTheSameDirectionsAsTheDetailedEngine() {
        RoadGraph detailed = block();
        NationalGraph graph = NationalFixture.of(detailed);
        // nearer the north side, so going round by Kuzey Sokak is clearly shorter
        GeoPoint from = GraphFixture.at(0.7, 0);
        GeoPoint to = GraphFixture.at(0.7, 2);

        Snap start = new SpatialIndex(detailed).nearest(from, 50, segment -> true).orElseThrow();
        Snap goal = new SpatialIndex(detailed).nearest(to, 50, segment -> true).orElseThrow();
        Path detailedPath = new AStarRouter(detailed).route(start, goal,
                Optimization.FASTEST.weighting(detailed, TravelMode.CAR, TrafficConditions.NONE, NOW)).orElseThrow();
        EdgeIndex index = new EdgeIndex(graph);
        Path nationalPath = new NationalRouter(graph).route(snap(index, from), snap(index, to),
                Optimization.FASTEST.weighting(graph, RoadTraffic.NONE)).orElseThrow();

        List<Instruction> expected = new InstructionBuilder(detailed).build(detailedPath, TravelMode.CAR);
        List<Instruction> actual = new InstructionBuilder(graph).build(nationalPath, TravelMode.CAR);
        assertThat(actual).extracting(Instruction::maneuver, Instruction::street)
                .containsExactlyElementsOf(expected.stream()
                        .map(step -> org.assertj.core.groups.Tuple.tuple(step.maneuver(), step.street())).toList());
    }

    @Test
    void trafficSlowsTheRouteDownButNotItsFreeFlowTime() {
        NationalGraph graph = NationalFixture.of(block());
        EdgeIndex index = new EdgeIndex(graph);
        EdgeSnap from = snap(index, GraphFixture.at(0.5, 0));
        EdgeSnap to = snap(index, GraphFixture.at(0.5, 2));
        CongestionProfile.Builder profile = CongestionProfile.builder();
        int[] half = new int[CongestionProfile.SLOTS];
        Arrays.fill(half, 50);
        Geohash.cover(40.995, 28.995, 41.005, 29.01).forEach(cell -> profile.add(cell, half));
        RoadTraffic busy = new RoadTraffic(profile.build(), new DayTime(DayTime.WEEKDAY, 8), 1);

        Path free = new NationalRouter(graph).route(from, to, Optimization.FASTEST.weighting(graph, RoadTraffic.NONE))
                .orElseThrow();
        Path slow = new NationalRouter(graph).route(from, to, Optimization.FASTEST.weighting(graph, busy))
                .orElseThrow();

        assertThat(slow.distanceMeters()).isCloseTo(free.distanceMeters(), within(0.01));
        assertThat(slow.durationSeconds()).isCloseTo(2 * free.durationSeconds(), within(0.01));
        assertThat(slow.freeFlowSeconds()).isCloseTo(free.durationSeconds(), within(0.01));
        assertThat(free.freeFlowSeconds()).isCloseTo(free.durationSeconds(), within(0.01));
    }

    @Test
    void measuresTheSameShortestRoutesAsTheDetailedEngineAcrossKadikoy() {
        RoadGraph detailed = KadikoyNetwork.graph();
        NationalGraph graph = NationalFixture.of(detailed);
        List<GeoPoint> points = carRoadMidpoints(detailed, graph, 24);

        for (int i = 0; i + 1 < points.size(); i += 2) {
            GeoPoint from = points.get(i);
            GeoPoint to = points.get(i + 1);
            assertThat(nationalShortest(graph, from, to)).as("%s -> %s", from, to)
                    .isCloseTo(detailedShortest(detailed, from, to), within(0.5));
        }
    }

    @Test
    void aStarFindsRoutesAsGoodAsASearchWithoutEstimateAndLooksAtFewerJunctions() {
        RoadGraph detailed = KadikoyNetwork.graph();
        NationalGraph graph = NationalFixture.of(detailed);
        EdgeIndex index = new EdgeIndex(graph);
        NationalRouter router = new NationalRouter(graph);
        Weighting fastest = Optimization.FASTEST.weighting(graph, RoadTraffic.NONE);
        Weighting dijkstra = new WithoutEstimate(fastest);
        List<GeoPoint> points = carRoadMidpoints(detailed, graph, 24);

        int aStarVisits = 0;
        int dijkstraVisits = 0;
        for (int i = 0; i + 1 < points.size(); i += 2) {
            EdgeSnap from = snap(index, points.get(i));
            EdgeSnap to = snap(index, points.get(i + 1));
            Optional<Path> aStar = router.route(from, to, fastest);
            Optional<Path> plain = router.route(from, to, dijkstra);
            assertThat(aStar.isPresent()).isEqualTo(plain.isPresent());
            if (aStar.isPresent()) {
                assertThat(aStar.get().cost()).isCloseTo(plain.get().cost(),
                        within(1e-6 * plain.get().cost() + 1e-9));
                aStarVisits += aStar.get().visitedNodes();
                dijkstraVisits += plain.get().visitedNodes();
            }
        }
        assertThat(aStarVisits).isLessThan(dijkstraVisits);
    }

    private static double nationalShortest(NationalGraph graph, GeoPoint from, GeoPoint to) {
        EdgeIndex index = new EdgeIndex(graph);
        return new NationalRouter(graph).route(snap(index, from), snap(index, to),
                        Optimization.SHORTEST.weighting(graph, RoadTraffic.NONE))
                .map(Path::distanceMeters).orElse(Double.POSITIVE_INFINITY);
    }

    private static double detailedShortest(RoadGraph graph, GeoPoint from, GeoPoint to) {
        SpatialIndex index = new SpatialIndex(graph);
        Snap start = index.nearest(from, 50, segment -> graph.allowsAny(segment, TravelMode.CAR)).orElseThrow();
        Snap goal = index.nearest(to, 50, segment -> graph.allowsAny(segment, TravelMode.CAR)).orElseThrow();
        return new AStarRouter(graph).route(start, goal,
                        Optimization.SHORTEST.weighting(graph, TravelMode.CAR, TrafficConditions.NONE, NOW))
                .map(Path::distanceMeters).orElse(Double.POSITIVE_INFINITY);
    }

    private static EdgeSnap snap(EdgeIndex index, GeoPoint point) {
        return index.nearest(point, 50, edge -> true).orElseThrow();
    }

    /**
     * Middles of random two-way car segments, where both graphs snap to the same road.
     */
    private static List<GeoPoint> carRoadMidpoints(RoadGraph detailed, NationalGraph graph, int count) {
        Random random = new Random(7);
        SpatialIndex detailedIndex = new SpatialIndex(detailed);
        EdgeIndex nationalIndex = new EdgeIndex(graph);
        List<GeoPoint> points = new ArrayList<>();
        while (points.size() < count) {
            int segment = random.nextInt(detailed.segmentCount());
            if (!detailed.allows(RoadGraph.entry(segment, RoadGraph.FORWARD), TravelMode.CAR)
                    || !detailed.allows(RoadGraph.entry(segment, RoadGraph.BACKWARD), TravelMode.CAR)
                    || detailed.length(segment) < 20) {
                continue;
            }
            GeoPoint from = detailed.point(detailed.from(segment));
            GeoPoint to = detailed.point(detailed.to(segment));
            GeoPoint middle = new GeoPoint((from.lat() + to.lat()) / 2, (from.lon() + to.lon()) / 2);
            Optional<Snap> onDetailed = detailedIndex.nearest(middle, 1,
                    candidate -> detailed.allowsAny(candidate, TravelMode.CAR));
            Optional<EdgeSnap> onNational = nationalIndex.nearest(middle, 1, edge -> true);
            if (onDetailed.isPresent() && onNational.isPresent() && onDetailed.get().segment() == segment
                    && graph.wayId(onNational.get().edge()) == detailed.wayId(segment)) {
                points.add(middle);
            }
        }
        return points;
    }

    /** The same costs with an estimate of zero: A* turns into Dijkstra's algorithm. */
    private record WithoutEstimate(Weighting weighting) implements Weighting {

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
    }
}
