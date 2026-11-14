package com.citynavigator.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.citynavigator.testsupport.KadikoyNetwork;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The engine on the real Kadıköy extract that ships with the application.
 */
class KadikoyRoutingTest {

    private static final GeoPoint PIER = new GeoPoint(40.9917, 29.0227);
    private static final GeoPoint MODA = new GeoPoint(40.9813, 29.0262);

    private final RoadGraph graph = KadikoyNetwork.graph();
    private final SpatialIndex index = new SpatialIndex(graph);
    private final AStarRouter router = new AStarRouter(graph);

    @Test
    void theExtractIsAWholeDistrict() {
        assertThat(graph.nodeCount()).isGreaterThan(50_000);
        assertThat(graph.segmentCount()).isGreaterThan(60_000);
        assertThat(graph.covers(PIER, 0)).isTrue();
    }

    @Test
    void drivesFromThePierToModaWithDirections() {
        Path path = route(PIER, MODA, TravelMode.CAR, Optimization.FASTEST).orElseThrow();
        List<Instruction> steps = new InstructionBuilder(graph).build(path, TravelMode.CAR);

        assertThat(path.distanceMeters()).isBetween(1_200.0, 3_000.0);
        assertThat(path.durationSeconds()).isBetween(120.0, 600.0);
        assertThat(steps.getFirst().maneuver()).isEqualTo(Maneuver.DEPART);
        assertThat(steps.getLast().maneuver()).isEqualTo(Maneuver.ARRIVE);
        assertThat(steps.size()).isBetween(3, 15);
    }

    @Test
    void walkingIsShorterThanDrivingThroughOneWayStreets() {
        double driving = route(PIER, MODA, TravelMode.CAR, Optimization.SHORTEST).orElseThrow().distanceMeters();
        double walking = route(PIER, MODA, TravelMode.FOOT, Optimization.SHORTEST).orElseThrow().distanceMeters();
        assertThat(walking).isLessThan(driving);
        assertThat(walking).isGreaterThan(GeoMath.distance(PIER, MODA));
    }

    @Test
    void aStarFindsTheSameCostAsDijkstraOnRealStreets() {
        Random random = new Random(42);
        double[] bounds = graph.bounds();
        int compared = 0;
        for (int trial = 0; trial < 60; trial++) {
            TravelMode mode = TravelMode.values()[trial % 3];
            GeoPoint from = new GeoPoint(bounds[0] + random.nextDouble() * (bounds[2] - bounds[0]),
                    bounds[1] + random.nextDouble() * (bounds[3] - bounds[1]));
            GeoPoint to = new GeoPoint(bounds[0] + random.nextDouble() * (bounds[2] - bounds[0]),
                    bounds[1] + random.nextDouble() * (bounds[3] - bounds[1]));
            Optional<Snap> start = index.nearest(from, 300, segment -> graph.allowsAny(segment, mode));
            Optional<Snap> goal = index.nearest(to, 300, segment -> graph.allowsAny(segment, mode));
            if (start.isEmpty() || goal.isEmpty()) {
                continue;
            }
            Weighting weighting = Optimization.FASTEST.weighting(graph, mode, TrafficConditions.NONE, Instant.EPOCH);
            Optional<Path> astar = router.route(start.get(), goal.get(), weighting);
            Optional<Path> dijkstra = router.route(start.get(), goal.get(), new NoEstimate(weighting));
            assertThat(astar.isPresent()).isEqualTo(dijkstra.isPresent());
            if (astar.isPresent()) {
                assertThat(astar.get().cost()).isCloseTo(dijkstra.get().cost(), within(1e-6));
                assertThat(astar.get().visitedNodes()).isLessThanOrEqualTo(dijkstra.get().visitedNodes());
                compared++;
            }
        }
        assertThat(compared).isGreaterThan(30);
    }

    private Optional<Path> route(GeoPoint from, GeoPoint to, TravelMode mode, Optimization optimization) {
        Snap start = index.nearest(from, 300, segment -> graph.allowsAny(segment, mode)).orElseThrow();
        Snap goal = index.nearest(to, 300, segment -> graph.allowsAny(segment, mode)).orElseThrow();
        return router.route(start, goal, optimization.weighting(graph, mode, TrafficConditions.NONE, Instant.EPOCH));
    }

    private record NoEstimate(Weighting weighting) implements Weighting {

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
