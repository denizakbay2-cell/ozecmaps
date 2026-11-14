package com.citynavigator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.citynavigator.dto.InstructionView;
import com.citynavigator.dto.RouteResponse;
import com.citynavigator.dto.TrafficStretch;
import com.citynavigator.exception.BusinessRuleException;
import com.citynavigator.external.OsrmClient;
import com.citynavigator.external.TrafficIndexClient;
import com.citynavigator.routing.CongestionProfile;
import com.citynavigator.routing.GeoPoint;
import com.citynavigator.routing.Optimization;
import com.citynavigator.routing.RoadClass;
import com.citynavigator.routing.RoadGraph;
import com.citynavigator.routing.TrafficConditions;
import com.citynavigator.routing.TravelMode;
import com.citynavigator.testsupport.Corridors;
import com.citynavigator.testsupport.Geohash;
import com.citynavigator.testsupport.GraphFixture;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RouteServiceTest {

    @Mock
    private RoutingEngine engine;
    @Mock
    private OsrmClient nationwide;
    @Mock
    private ExternalServices external;
    @Mock
    private TrafficIndexClient index;
    private final NationalEngine national = new NationalEngine();
    private RouteService service;
    private final RoadGraph graph = ServiceFixture.town();

    @BeforeEach
    void setUp() {
        lenient().when(engine.snapshot()).thenReturn(RoutingEngine.Snapshot.of(graph, TrafficConditions.NONE));
        service = withTraffic(CongestionProfile.NONE);
    }

    private RouteService withTraffic(CongestionProfile profile) {
        TrafficService traffic = new TrafficService(ServiceFixture.CLOCK, index, external, engine, national, profile);
        return new RouteService(engine, national, traffic, ServiceFixture.CLOCK, nationwide, external);
    }

    @Test
    void plansARouteWithTurkishDirections() {
        RouteResponse route = service.plan(GraphFixture.at(0.05, 0.1), GraphFixture.at(0.9, 1), TravelMode.CAR,
                Optimization.FASTEST, null);

        assertThat(route.mode()).isEqualTo("car");
        assertThat(route.optimize()).isEqualTo("fastest");
        assertThat(route.engine()).isEqualTo("astar");
        assertThat(route.distanceMeters()).isCloseTo(180, within(2.0));
        assertThat(route.from().street()).isEqualTo("Ana Cadde");
        assertThat(route.from().distanceMeters()).isEqualTo(5);
        assertThat(route.to().street()).isEqualTo("Yan Sokak");
        assertThat(route.geometry()).hasSize(3);
        assertThat(route.geometry().getFirst()[0]).isEqualTo(GraphFixture.ORIGIN_LAT);
        assertThat(route.instructions()).extracting(InstructionView::maneuver, InstructionView::text,
                InstructionView::street).containsExactly(
                org.assertj.core.groups.Tuple.tuple("DEPART", "Düz devam edin", "Ana Cadde"),
                org.assertj.core.groups.Tuple.tuple("LEFT", "Sola dönün", "Yan Sokak"),
                org.assertj.core.groups.Tuple.tuple("ARRIVE", "Hedefinize ulaştınız", null));
        assertThat(route.incidentIds()).isEmpty();
        assertThat(route.visitedNodes()).isPositive();
        assertThat(route.departAt()).isEqualTo(ServiceFixture.NOW);
        assertThat(route.trafficDelaySeconds()).isZero();
    }

    @Test
    void carsMeetTheUsualTrafficOfTheirDepartureTimeAndWalkersDoNot() {
        int[] half = new int[72];
        Arrays.fill(half, 50);
        CongestionProfile.Builder profile = CongestionProfile.builder();
        Geohash.cover(40.995, 28.995, 41.01, 29.01).forEach(cell -> profile.add(cell, half));
        RouteService busy = withTraffic(profile.build());
        GeoPoint from = GraphFixture.at(0.05, 0.1);
        GeoPoint to = GraphFixture.at(0.9, 1);

        RouteResponse free = service.plan(from, to, TravelMode.CAR, Optimization.FASTEST, null);
        RouteResponse jammed = busy.plan(from, to, TravelMode.CAR, Optimization.FASTEST, null);
        RouteResponse walk = busy.plan(from, to, TravelMode.FOOT, Optimization.FASTEST, null);

        assertThat(jammed.durationSeconds()).isCloseTo(2 * free.durationSeconds(), within(1.0));
        assertThat(jammed.trafficDelaySeconds()).isCloseTo((long) free.durationSeconds(), within(1L));
        assertThat(walk.trafficDelaySeconds()).isZero();
        // the whole route runs at half speed: one slow stretch from the first point to the last
        assertThat(jammed.traffic()).singleElement().isEqualTo(
                new TrafficStretch(0, jammed.geometry().size() - 1, 0.5));
        assertThat(free.traffic()).isEmpty();
        assertThat(walk.traffic()).isEmpty();
    }

    @Test
    void plansForTheRequestedDepartureWithinAWeek() {
        Instant evening = ServiceFixture.NOW.plus(Duration.ofHours(6));
        GeoPoint from = GraphFixture.at(0.05, 0.1);
        GeoPoint to = GraphFixture.at(0.9, 1);

        assertThat(service.plan(from, to, TravelMode.CAR, Optimization.FASTEST, evening).departAt()).isEqualTo(evening);
        assertThatThrownBy(() -> service.plan(from, to, TravelMode.CAR, Optimization.FASTEST,
                ServiceFixture.NOW.plus(Duration.ofDays(8))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("7 gün");
        assertThatThrownBy(() -> service.plan(from, to, TravelMode.CAR, Optimization.FASTEST,
                ServiceFixture.NOW.minus(Duration.ofDays(2))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("1 gün");
    }

    @Test
    void offersAClearlyDifferentSecondRouteOnlyWhenAskedFor() {
        RoadGraph corridors = Corridors.of(RoadClass.PRIMARY, RoadClass.SECONDARY).build();
        TrafficConditions roadwork = TrafficConditions.NONE.with(
                TrafficConditions.Restriction.slowdown(9, 0.9, null), List.of(corridors.segment(32, 1)));
        when(engine.snapshot()).thenReturn(RoutingEngine.Snapshot.of(corridors, roadwork));

        RouteResponse route = service.plan(Corridors.START, Corridors.DESTINATION, TravelMode.CAR,
                Optimization.FASTEST, null, true);

        assertThat(route.instructions()).extracting(InstructionView::street).contains("Kuzey Yolu");
        assertThat(route.incidentIds()).isEmpty();
        RouteResponse other = route.alternatives().getFirst();
        assertThat(route.alternatives()).hasSize(1);
        assertThat(other.engine()).isEqualTo("astar");
        assertThat(other.instructions()).extracting(InstructionView::street).contains("Güney Yolu")
                .doesNotContain("Kuzey Yolu");
        assertThat(other.durationSeconds()).isGreaterThan(route.durationSeconds());
        assertThat(other.incidentIds()).containsExactly(9L);
        assertThat(other.alternatives()).isEmpty();
        assertThat(service.plan(Corridors.START, Corridors.DESTINATION, TravelMode.CAR, Optimization.FASTEST, null)
                .alternatives()).isEmpty();
    }

    @Test
    void plansThroughAStopAndJoinsTheLegsIntoOneRoute() {
        GeoPoint from = GraphFixture.at(0, 0.1);
        GeoPoint stop = GraphFixture.at(0.9, 1);
        GeoPoint to = GraphFixture.at(0, 1.9);
        RouteResponse first = service.plan(from, stop, TravelMode.CAR, Optimization.FASTEST, null);
        RouteResponse second = service.plan(stop, to, TravelMode.CAR, Optimization.FASTEST, null);

        RouteResponse route = service.plan(from, List.of(stop), to, TravelMode.CAR, Optimization.FASTEST, null, true);

        assertThat(route.distanceMeters()).isEqualTo(first.distanceMeters() + second.distanceMeters());
        assertThat(route.durationSeconds()).isEqualTo(first.durationSeconds() + second.durationSeconds());
        // the legs share the stop: the joined line has one point less than the two together
        assertThat(route.geometry()).hasSize(first.geometry().size() + second.geometry().size() - 1);
        assertThat(route.instructions()).extracting(InstructionView::maneuver).containsExactly(
                "DEPART", "LEFT", "WAYPOINT", "CONTINUE", "LEFT", "ARRIVE");
        InstructionView reached = route.instructions().get(2);
        assertThat(reached.text()).isEqualTo("Ara durağa ulaştınız");
        assertThat(reached.pointIndex()).isEqualTo(first.geometry().size() - 1);
        assertThat(route.instructions().getLast().pointIndex()).isEqualTo(route.geometry().size() - 1);
        assertThat(route.from()).isEqualTo(first.from());
        assertThat(route.to()).isEqualTo(second.to());
        assertThat(route.alternatives()).isEmpty();
    }

    @Test
    void namesTheLegOfAStopThatCannotBeReachedAndLimitsTheStops() {
        GeoPoint from = GraphFixture.at(0, 0.1);
        GeoPoint to = GraphFixture.at(0, 1.9);

        assertThatThrownBy(() -> service.plan(from, List.of(GraphFixture.at(-4, 1)), to, TravelMode.CAR,
                Optimization.FASTEST, null, false))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Ara durağa giden yol: Varış noktasının 300 m yakınında araç yolu yok");
        GeoPoint stop = GraphFixture.at(0, 1);
        assertThatThrownBy(() -> service.plan(from, List.of(stop, stop, stop, stop), to, TravelMode.CAR,
                Optimization.FASTEST, null, false))
                .isInstanceOf(BusinessRuleException.class).hasMessage("En fazla 3 ara durak eklenebilir");
    }

    @Test
    void listsTheIncidentsThatSlowTheRouteDown() {
        TrafficConditions traffic = TrafficConditions.NONE.with(TrafficConditions.Restriction.slowdown(7, 0.5, null),
                List.of(graph.segment(10, 0)));
        when(engine.snapshot()).thenReturn(RoutingEngine.Snapshot.of(graph, traffic));

        RouteResponse route = service.plan(GraphFixture.at(0, 0.1), GraphFixture.at(0, 1.9), TravelMode.CAR,
                Optimization.FASTEST, null);

        assertThat(route.incidentIds()).containsExactly(7L);
        // only the first piece, up to the junction, runs at half speed
        assertThat(route.traffic()).containsExactly(new TrafficStretch(0, 1, 0.5));
    }

    @Test
    void rejectsPointsOutsideTheMapWhenTheNationwideServiceIsOff() {
        GeoPoint ankara = new GeoPoint(39.92, 32.85);
        assertThatThrownBy(() -> service.plan(ankara, GraphFixture.at(0, 1), TravelMode.CAR, Optimization.FASTEST, null))
                .isInstanceOf(BusinessRuleException.class).hasMessage("Başlangıç noktası harita alanının dışında");
        assertThatThrownBy(() -> service.plan(GraphFixture.at(0, 1), ankara, TravelMode.CAR, Optimization.FASTEST, null))
                .isInstanceOf(BusinessRuleException.class).hasMessage("Varış noktası harita alanının dışında");
    }

    @Test
    void rejectsPointsFarFromAUsableRoad() {
        assertThatThrownBy(() -> service.plan(GraphFixture.at(-4, 1), GraphFixture.at(0, 1), TravelMode.CAR,
                Optimization.FASTEST, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Başlangıç noktasının 300 m yakınında araç yolu yok");
    }

    @Test
    void rejectsARouteToTheSameSpot() {
        assertThatThrownBy(() -> service.plan(GraphFixture.at(0, 1), GraphFixture.at(0.001, 1), TravelMode.FOOT,
                Optimization.SHORTEST, null))
                .isInstanceOf(BusinessRuleException.class).hasMessage("Başlangıç ve varış noktası aynı yerde");
    }

    @Test
    void explainsWhenNoRouteConnectsThePoints() {
        assertThatThrownBy(() -> service.plan(GraphFixture.at(0, 1), GraphFixture.at(5, 5.5), TravelMode.BICYCLE,
                Optimization.FASTEST, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Bu iki nokta arasında bisikletle gidilebilecek bir yol bulunamadı");
    }
}
