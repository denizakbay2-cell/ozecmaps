package com.citynavigator.osm;

import static org.assertj.core.api.Assertions.assertThat;

import com.citynavigator.routing.RoadClass;
import com.citynavigator.routing.RoadGraph;
import com.citynavigator.routing.RoadWay;
import com.citynavigator.routing.TravelMode;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RoadTagsTest {

    @Test
    void aResidentialStreetIsOpenToEveryoneBothWays() {
        RoadWay way = interpret(Map.of("highway", "residential", "name", " Moda Caddesi ")).orElseThrow();
        assertThat(way.roadClass()).isEqualTo(RoadClass.RESIDENTIAL);
        assertThat(way.name()).isEqualTo("Moda Caddesi");
        for (TravelMode mode : TravelMode.values()) {
            assertThat(way.allows(mode, RoadGraph.FORWARD)).isTrue();
            assertThat(way.allows(mode, RoadGraph.BACKWARD)).isTrue();
        }
    }

    @Test
    void oneWayStreetsBindVehiclesButNotWalkers() {
        RoadWay forward = interpret(Map.of("highway", "tertiary", "oneway", "yes")).orElseThrow();
        assertThat(forward.allows(TravelMode.CAR, RoadGraph.FORWARD)).isTrue();
        assertThat(forward.allows(TravelMode.CAR, RoadGraph.BACKWARD)).isFalse();
        assertThat(forward.allows(TravelMode.BICYCLE, RoadGraph.BACKWARD)).isFalse();
        assertThat(forward.allows(TravelMode.FOOT, RoadGraph.BACKWARD)).isTrue();

        RoadWay backward = interpret(Map.of("highway", "tertiary", "oneway", "-1")).orElseThrow();
        assertThat(backward.allows(TravelMode.CAR, RoadGraph.FORWARD)).isFalse();
        assertThat(backward.allows(TravelMode.CAR, RoadGraph.BACKWARD)).isTrue();

        RoadWay explicitTwoWay = interpret(Map.of("highway", "motorway", "oneway", "no")).orElseThrow();
        assertThat(explicitTwoWay.allows(TravelMode.CAR, RoadGraph.BACKWARD)).isTrue();
    }

    @Test
    void roundaboutsAndMotorwaysAreOneWayWithoutSayingSo() {
        RoadWay roundabout = interpret(Map.of("highway", "primary", "junction", "roundabout")).orElseThrow();
        assertThat(roundabout.roundabout()).isTrue();
        assertThat(roundabout.allows(TravelMode.CAR, RoadGraph.BACKWARD)).isFalse();

        RoadWay motorway = interpret(Map.of("highway", "motorway", "ref", "O-1")).orElseThrow();
        assertThat(motorway.name()).isEqualTo("O-1");
        assertThat(motorway.allows(TravelMode.CAR, RoadGraph.BACKWARD)).isFalse();
        assertThat(motorway.allowsAny(TravelMode.FOOT)).isFalse();
        assertThat(motorway.allowsAny(TravelMode.BICYCLE)).isFalse();
    }

    @Test
    void cyclistsMayRideAgainstAOneWayWhenTaggedSo() {
        RoadWay contraflow = interpret(Map.of("highway", "residential", "oneway", "yes", "oneway:bicycle", "no"))
                .orElseThrow();
        assertThat(contraflow.allows(TravelMode.BICYCLE, RoadGraph.BACKWARD)).isTrue();
        assertThat(contraflow.allows(TravelMode.CAR, RoadGraph.BACKWARD)).isFalse();

        RoadWay lane = interpret(Map.of("highway", "residential", "oneway", "yes", "cycleway", "opposite_lane"))
                .orElseThrow();
        assertThat(lane.allows(TravelMode.BICYCLE, RoadGraph.BACKWARD)).isTrue();

        RoadWay bikeOneWay = interpret(Map.of("highway", "cycleway", "oneway:bicycle", "yes")).orElseThrow();
        assertThat(bikeOneWay.allows(TravelMode.BICYCLE, RoadGraph.BACKWARD)).isFalse();
    }

    @Test
    void footwaysAreForWalkersAndPushedBikesOnly() {
        RoadWay footway = interpret(Map.of("highway", "footway")).orElseThrow();
        assertThat(footway.allowsAny(TravelMode.CAR)).isFalse();
        assertThat(footway.allowsAny(TravelMode.BICYCLE)).isTrue();
        assertThat(footway.allowsAny(TravelMode.FOOT)).isTrue();

        RoadWay noBikes = interpret(Map.of("highway", "footway", "bicycle", "no")).orElseThrow();
        assertThat(noBikes.allowsAny(TravelMode.BICYCLE)).isFalse();

        RoadWay steps = interpret(Map.of("highway", "steps")).orElseThrow();
        assertThat(steps.access()).isEqualTo(TravelMode.FOOT.accessBits(true, true));
    }

    @Test
    void accessTagsCloseAndReopenRoads() {
        assertThat(interpret(Map.of("highway", "service", "access", "private"))).isEmpty();
        RoadWay walkersOnly = interpret(Map.of("highway", "service", "access", "no", "foot", "yes")).orElseThrow();
        assertThat(walkersOnly.access()).isEqualTo(TravelMode.FOOT.accessBits(true, true));
        RoadWay noCars = interpret(Map.of("highway", "residential", "motor_vehicle", "no")).orElseThrow();
        assertThat(noCars.allowsAny(TravelMode.CAR)).isFalse();
        assertThat(noCars.allowsAny(TravelMode.BICYCLE)).isTrue();
        RoadWay trackForCars = interpret(Map.of("highway", "track", "motorcar", "yes")).orElseThrow();
        assertThat(trackForCars.allowsAny(TravelMode.CAR)).isTrue();
    }

    @Test
    void ignoresWhatIsNotARoad() {
        assertThat(interpret(Map.of("building", "yes"))).isEmpty();
        assertThat(interpret(Map.of("highway", "proposed"))).isEmpty();
        assertThat(interpret(Map.of("highway", "pedestrian", "area", "yes"))).isEmpty();
    }

    @Test
    void readsSpeedLimitsInTheUsualNotations() {
        assertThat(RoadTags.maxSpeed("50")).isEqualTo(50);
        assertThat(RoadTags.maxSpeed("30 mph")).isEqualTo(48);
        assertThat(RoadTags.maxSpeed("TR:urban")).isEqualTo(50);
        assertThat(RoadTags.maxSpeed("walk")).isEqualTo(5);
        assertThat(RoadTags.maxSpeed("signals")).isZero();
        assertThat(RoadTags.maxSpeed("200")).isZero();
        assertThat(RoadTags.maxSpeed(null)).isZero();
    }

    @Test
    void namesFallBackToTheTurkishNameAndTheRoadNumber() {
        assertThat(RoadTags.nameOf(Map.of("name", " ", "name:tr", "Bağdat Caddesi"))).isEqualTo("Bağdat Caddesi");
        assertThat(RoadTags.nameOf(Map.of("ref", "D-100"))).isEqualTo("D-100");
        assertThat(RoadTags.nameOf(Map.of())).isNull();
        assertThat(RoadTags.nameOf(Map.of("name", "x".repeat(250)))).hasSize(RoadTags.MAX_NAME_LENGTH);
    }

    private static Optional<RoadWay> interpret(Map<String, String> tags) {
        return RoadTags.interpret(1, tags);
    }
}
