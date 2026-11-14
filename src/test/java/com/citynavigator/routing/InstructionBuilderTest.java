package com.citynavigator.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

import com.citynavigator.testsupport.GraphFixture;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class InstructionBuilderTest {

    /**
     * A north-south main street crossed by an east and a west street at node 2.
     * <pre>
     *            3
     *            |
     *   5 ------ 2 ------ 4
     *            |
     *            1
     * </pre>
     */
    private static GraphFixture crossing() {
        return new GraphFixture()
                .node(1, 0, 0).node(2, 1, 0).node(3, 2, 0).node(4, 1, 1).node(5, 1, -1)
                .way("Batı Sokak", 2, 5)
                .way("Doğu Sokak", 2, 4);
    }

    @Test
    void announcesATurnOntoAnotherStreet() {
        RoadGraph graph = crossing().way("Ana Cadde", 1, 2, 3).build();
        List<Instruction> steps = directions(graph, GraphFixture.at(0.1, 0), GraphFixture.at(1, 0.9), TravelMode.CAR);

        assertThat(steps).extracting(Instruction::maneuver, Instruction::street).containsExactly(
                tuple(Maneuver.DEPART, "Ana Cadde"),
                tuple(Maneuver.RIGHT, "Doğu Sokak"),
                tuple(Maneuver.ARRIVE, null));
        assertThat(steps.get(0).bearing()).isCloseTo(0, within(1.0));
        assertThat(steps.get(0).distanceMeters()).isCloseTo(90, within(1.0));
        assertThat(steps.get(1).distanceMeters()).isCloseTo(90, within(1.0));
        assertThat(steps.get(1).roadClass()).isEqualTo(RoadClass.RESIDENTIAL);
    }

    @Test
    void saysNothingWhileTheRouteFollowsTheSameStreetThroughAJunction() {
        RoadGraph graph = crossing().way("Ana Cadde", 1, 2, 3).build();
        assertThat(directions(graph, GraphFixture.at(0.1, 0), GraphFixture.at(1.9, 0), TravelMode.CAR))
                .extracting(Instruction::maneuver).containsExactly(Maneuver.DEPART, Maneuver.ARRIVE);
    }

    @Test
    void announcesANewNameWhenGoingStraightOn() {
        RoadGraph graph = crossing().way("Ana Cadde", 1, 2).way("Yeni Cadde", 2, 3).build();
        assertThat(directions(graph, GraphFixture.at(0.1, 0), GraphFixture.at(1.9, 0), TravelMode.CAR))
                .extracting(Instruction::maneuver, Instruction::street).containsExactly(
                        tuple(Maneuver.DEPART, "Ana Cadde"),
                        tuple(Maneuver.CONTINUE, "Yeni Cadde"),
                        tuple(Maneuver.ARRIVE, null));
    }

    @Test
    void saysNothingWhenTheRoadGetsItsNameBackAfterABridge() {
        // the motorway is called "Sakarya Köprüsü" between 2 and 3; at 3 an exit leaves to the east
        RoadGraph graph = new GraphFixture()
                .node(1, 0, 0).node(2, 2, 0).node(3, 4, 0).node(4, 8, 0).node(6, 5, 1)
                .way("Anadolu Otoyolu", 1, 2)
                .way("Sakarya Köprüsü", 2, 3)
                .way("Anadolu Otoyolu", 3, 4)
                .way("Çıkış", 3, 6)
                .build();
        assertThat(directions(graph, GraphFixture.at(0.1, 0), GraphFixture.at(7.9, 0), TravelMode.CAR))
                .extracting(Instruction::maneuver, Instruction::street).containsExactly(
                        tuple(Maneuver.DEPART, "Anadolu Otoyolu"),
                        tuple(Maneuver.ARRIVE, null));
    }

    @Test
    void aTurnInTheLastMetresIsPartOfArriving() {
        RoadGraph graph = crossing().way("Ana Cadde", 1, 2, 3).build();
        assertThat(directions(graph, GraphFixture.at(0.1, 0), GraphFixture.at(1, 0.2), TravelMode.CAR))
                .extracting(Instruction::maneuver).containsExactly(Maneuver.DEPART, Maneuver.ARRIVE);
    }

    @Test
    void saysNothingWhereTheRoadOnlyBends() {
        RoadGraph graph = new GraphFixture()
                .node(1, 0, 0).node(2, 1, 0).node(3, 1, 1)
                .way("Kıvrım Sokak", 1, 2, 3)
                .build();
        assertThat(directions(graph, GraphFixture.at(0.1, 0), GraphFixture.at(1, 0.9), TravelMode.CAR))
                .extracting(Instruction::maneuver).containsExactly(Maneuver.DEPART, Maneuver.ARRIVE);
    }

    @Test
    void announcesLeavingTheNaturalContinuationOfTheSameStreet() {
        // "Ana Cadde" goes on straight north, but a branch with the same name turns left at node 2
        RoadGraph graph = new GraphFixture()
                .node(1, 0, 0).node(2, 1, 0).node(3, 2, 0).node(5, 1, -1)
                .way("Ana Cadde", 1, 2, 3)
                .way("Ana Cadde", 2, 5)
                .build();
        assertThat(directions(graph, GraphFixture.at(0.1, 0), GraphFixture.at(1, -0.9), TravelMode.CAR))
                .extracting(Instruction::maneuver).containsExactly(Maneuver.DEPART, Maneuver.LEFT, Maneuver.ARRIVE);
    }

    @Test
    void countsTheExitsOfARoundabout() {
        // ring S(10) -> E(11) -> N(12) -> W(13) -> S, counter-clockwise as in right-hand traffic
        RoadGraph graph = new GraphFixture()
                .node(10, -1, 0).node(11, 0, 1).node(12, 1, 0).node(13, 0, -1)
                .node(20, -3, 0).node(21, 0, 3).node(22, 3, 0).node(23, 0, -3)
                .roundabout(10, 11, 12, 13, 10)
                .way("Güney Cadde", 20, 10).way("Doğu Cadde", 11, 21)
                .way("Kuzey Cadde", 12, 22).way("Batı Cadde", 13, 23)
                .build();

        List<Instruction> toNorth = directions(graph, GraphFixture.at(-2.9, 0), GraphFixture.at(2.9, 0),
                TravelMode.CAR);
        assertThat(toNorth).extracting(Instruction::maneuver, Instruction::street, Instruction::exitNumber)
                .containsExactly(
                        tuple(Maneuver.DEPART, "Güney Cadde", 0),
                        tuple(Maneuver.ROUNDABOUT, "Kuzey Cadde", 2),
                        tuple(Maneuver.ARRIVE, null, 0));

        assertThat(directions(graph, GraphFixture.at(-2.9, 0), GraphFixture.at(0, -2.9), TravelMode.CAR))
                .extracting(Instruction::exitNumber).containsExactly(0, 3, 0);
        assertThat(directions(graph, GraphFixture.at(-2.9, 0), GraphFixture.at(0, 2.9), TravelMode.CAR))
                .extracting(Instruction::exitNumber).containsExactly(0, 1, 0);
    }

    @Test
    void mergesTwoQuickTurnsTheSameWayIntoOne() {
        // north on A, 20 m west on B, then south on C: one U-turn instead of two lefts
        RoadGraph graph = new GraphFixture()
                .node(1, 0, 0).node(2, 1, 0).node(3, 2, 0)
                .node(4, 1, -0.2).node(5, 1, -2).node(6, 0, -0.2)
                .way("A Sokak", 1, 2, 3)
                .way("B Sokak", 2, 4, 5)
                .way("C Sokak", 4, 6)
                .build();
        assertThat(directions(graph, GraphFixture.at(0.1, 0), GraphFixture.at(0.1, -0.2), TravelMode.CAR))
                .extracting(Instruction::maneuver, Instruction::street).containsExactly(
                        tuple(Maneuver.DEPART, "A Sokak"),
                        tuple(Maneuver.U_TURN, "C Sokak"),
                        tuple(Maneuver.ARRIVE, null));
    }

    @Test
    void keepsAJogToTheRightAndLeftAsTwoSteps() {
        RoadGraph graph = new GraphFixture()
                .node(1, 0, 0).node(2, 1, 0).node(3, 2, 0)
                .node(4, 1, 0.2).node(5, 1, 2).node(6, 2, 0.2)
                .way("A Sokak", 1, 2, 3)
                .way("B Sokak", 2, 4, 5)
                .way("C Sokak", 4, 6)
                .build();
        assertThat(directions(graph, GraphFixture.at(0.1, 0), GraphFixture.at(1.9, 0.2), TravelMode.CAR))
                .extracting(Instruction::maneuver)
                .containsExactly(Maneuver.DEPART, Maneuver.RIGHT, Maneuver.LEFT, Maneuver.ARRIVE);
    }

    @Test
    void stepDistancesAddUpToTheRoute() {
        RoadGraph graph = crossing().way("Ana Cadde", 1, 2, 3).build();
        Path path = path(graph, GraphFixture.at(0.1, 0), GraphFixture.at(1, -0.5), TravelMode.FOOT);
        List<Instruction> steps = new InstructionBuilder(graph).build(path, TravelMode.FOOT);
        assertThat(steps.stream().mapToDouble(Instruction::distanceMeters).sum())
                .isCloseTo(path.distanceMeters(), within(1e-6));
        assertThat(steps.stream().mapToDouble(Instruction::durationSeconds).sum())
                .isCloseTo(path.durationSeconds(), within(1e-6));
        assertThat(steps.getLast().pointIndex()).isEqualTo(path.pointCount() - 1);
    }

    @Test
    void classifiesAnglesIntoManeuvers() {
        assertThat(Maneuver.ofAngle(5)).isEqualTo(Maneuver.CONTINUE);
        assertThat(Maneuver.ofAngle(-35)).isEqualTo(Maneuver.SLIGHT_LEFT);
        assertThat(Maneuver.ofAngle(35)).isEqualTo(Maneuver.SLIGHT_RIGHT);
        assertThat(Maneuver.ofAngle(-90)).isEqualTo(Maneuver.LEFT);
        assertThat(Maneuver.ofAngle(140)).isEqualTo(Maneuver.SHARP_RIGHT);
        assertThat(Maneuver.ofAngle(-140)).isEqualTo(Maneuver.SHARP_LEFT);
        assertThat(Maneuver.ofAngle(175)).isEqualTo(Maneuver.U_TURN);
    }

    private static List<Instruction> directions(RoadGraph graph, GeoPoint from, GeoPoint to, TravelMode mode) {
        return new InstructionBuilder(graph).build(path(graph, from, to, mode), mode);
    }

    private static Path path(RoadGraph graph, GeoPoint from, GeoPoint to, TravelMode mode) {
        SpatialIndex index = new SpatialIndex(graph);
        Snap start = index.nearest(from, 100, segment -> graph.allowsAny(segment, mode)).orElseThrow();
        Snap goal = index.nearest(to, 100, segment -> graph.allowsAny(segment, mode)).orElseThrow();
        return new AStarRouter(graph).route(start, goal, Optimization.FASTEST.weighting(graph, mode,
                TrafficConditions.NONE, Instant.EPOCH)).orElseThrow();
    }

    @Test
    void anEmptyPathOnlyArrives() {
        RoadGraph graph = crossing().way("Ana Cadde", 1, 2, 3).build();
        Path path = path(graph, GraphFixture.at(0.1, 0), GraphFixture.at(0.1, 0), TravelMode.CAR);
        assertThat(new InstructionBuilder(graph).build(path, TravelMode.CAR))
                .extracting(Instruction::maneuver).containsExactly(Maneuver.ARRIVE);
    }
}
