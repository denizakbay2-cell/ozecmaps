package com.citynavigator.service;

import static com.citynavigator.testsupport.GraphFixture.ALL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.citynavigator.dto.InstructionView;
import com.citynavigator.dto.RouteResponse;
import com.citynavigator.exception.BusinessRuleException;
import com.citynavigator.exception.UnavailableException;
import com.citynavigator.external.ExternalRoute;
import com.citynavigator.external.OsrmClient;
import com.citynavigator.external.TrafficIndexClient;
import com.citynavigator.routing.CongestionProfile;
import com.citynavigator.routing.EdgeSnap;
import com.citynavigator.routing.GeoPoint;
import com.citynavigator.routing.Instruction;
import com.citynavigator.routing.Maneuver;
import com.citynavigator.routing.NationalGraph;
import com.citynavigator.routing.Optimization;
import com.citynavigator.routing.Path;
import com.citynavigator.routing.RoadClass;
import com.citynavigator.routing.RoadTraffic;
import com.citynavigator.routing.TravelMode;
import com.citynavigator.routing.Weighting;
import com.citynavigator.testsupport.Geohash;
import com.citynavigator.testsupport.GraphFixture;
import com.citynavigator.testsupport.NationalFixture;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Car routes that leave the detailed area, on the own national graph.
 */
@ExtendWith(MockitoExtension.class)
class NationalRoutingTest {

    private static final GeoPoint IN_TOWN = GraphFixture.at(0, 1.5);
    private static final GeoPoint VILLAGE = GraphFixture.at(50, 2);
    private static final GeoPoint ISLAND = GraphFixture.at(50, 40.5);
    private static final GeoPoint NOWHERE = GraphFixture.at(30, 20);

    @Mock
    private RoutingEngine engine;
    @Mock
    private OsrmClient osrm;
    @Mock
    private ExternalServices external;
    @Mock
    private TrafficIndexClient index;
    private NationalEngine national;

    /**
     * The town of {@link ServiceFixture}, a village 5 km north linked to it by the D-100 and, from the end of
     * Fabrika Yolu, by Eski Yol, and an island road 4 km east of the village that no road reaches. Side roads make
     * junctions where the D-100 starts and ends.
     */
    private static NationalGraph region() {
        return NationalFixture.of(new GraphFixture()
                .node(1, 0, 0).node(2, 0, 1).node(3, 0, 2).node(4, 1, 1).node(5, 1, 3)
                .node(20, 50, 1).node(21, 50, 3).node(22, 60, 1)
                .node(30, 50, 40).node(31, 50, 41)
                .way(10, "Ana Cadde", RoadClass.PRIMARY, false, ALL, 1, 2, 3)
                .way(11, "Yan Sokak", RoadClass.RESIDENTIAL, false, ALL, 2, 4)
                .way(12, "Fabrika Yolu", RoadClass.RESIDENTIAL, false, ALL, 4, 5)
                .way(15, "D-100", RoadClass.TRUNK, false, ALL, 4, 20)
                .way(19, "Eski Yol", RoadClass.TRUNK, false, ALL, 5, 21)
                .way(16, "Köy Yolu", RoadClass.TERTIARY, false, ALL, 20, 21)
                .way(18, "Dağ Yolu", RoadClass.TERTIARY, false, ALL, 20, 22)
                .way(17, "Ada Yolu", RoadClass.RESIDENTIAL, false, ALL, 30, 31)
                .build());
    }

    @BeforeEach
    void setUp() {
        lenient().when(engine.snapshot()).thenReturn(ServiceFixture.snapshot());
        lenient().when(external.enabled()).thenReturn(true);
        lenient().when(index.current()).thenReturn(Optional.empty());
        national = new NationalEngine();
        national.use(region());
    }

    private RouteService routes(NationalEngine on, CongestionProfile profile) {
        TrafficService traffic = new TrafficService(ServiceFixture.CLOCK, index, external, engine, on, profile);
        return new RouteService(engine, on, traffic, ServiceFixture.CLOCK, osrm, external);
    }

    private RouteService routes() {
        return routes(national, CongestionProfile.NONE);
    }

    @Test
    void carRoutesLeavingTheAreaUseTheOwnNationalEngine() {
        RouteResponse route = routes().plan(IN_TOWN, VILLAGE, TravelMode.CAR, Optimization.FASTEST, null);

        assertThat(route.engine()).isEqualTo("national");
        assertThat(route.mode()).isEqualTo("car");
        assertThat(route.distanceMeters()).isBetween(5_100.0, 5_200.0);
        assertThat(route.from().street()).isEqualTo("Ana Cadde");
        assertThat(route.to().street()).isEqualTo("Köy Yolu");
        assertThat(route.instructions()).extracting(InstructionView::maneuver, InstructionView::street)
                .containsExactly(tuple("DEPART", "Ana Cadde"), tuple("RIGHT", "Yan Sokak"),
                        tuple("CONTINUE", "D-100"), tuple("RIGHT", "Köy Yolu"), tuple("ARRIVE", null));
        assertThat(route.instructions().getFirst().maneuver()).isEqualTo("DEPART");
        assertThat(route.instructions().getLast().maneuver()).isEqualTo("ARRIVE");
        assertThat(route.departAt()).isEqualTo(ServiceFixture.NOW);
        assertThat(route.trafficDelaySeconds()).isZero();
        assertThat(route.visitedNodes()).isPositive();
        verify(osrm, never()).routes(any(), any(), any(), anyInt());
    }

    @Test
    void theOldRoadIsTheAlternativeOnTheNationalGraph() {
        RouteResponse route = routes().plan(IN_TOWN, VILLAGE, TravelMode.CAR, Optimization.FASTEST, null, true);

        // Eski Yol only bends into the roads at its ends, so no instruction names it; its line shows it
        double eskiYol = GraphFixture.at(0, 3).lon();
        assertThat(route.geometry()).noneSatisfy(point -> assertThat(point[1]).isCloseTo(eskiYol, within(1e-6)));
        assertThat(route.alternatives()).singleElement().satisfies(other -> {
            assertThat(other.engine()).isEqualTo("national");
            assertThat(other.geometry()).anySatisfy(point -> assertThat(point[1]).isCloseTo(eskiYol, within(1e-6)));
            assertThat(other.instructions()).extracting(InstructionView::street).contains("Fabrika Yolu")
                    .doesNotContain("D-100");
            assertThat(other.durationSeconds()).isBetween(route.durationSeconds(), 1.3 * route.durationSeconds());
        });
    }

    @Test
    void aBusyNationalEngineSkipsTheAlternativeButNotTheRoute() {
        NationalEngine oneSlot = new NationalEngine(1, Duration.ofSeconds(1));
        oneSlot.use(region());
        NationalEngine.Network network = oneSlot.network().orElseThrow();
        EdgeSnap from = network.index().nearest(IN_TOWN, 50, edge -> true).orElseThrow();
        EdgeSnap to = network.index().nearest(VILLAGE, 50, edge -> true).orElseThrow();
        Weighting fastest = Optimization.FASTEST.weighting(network.graph(), RoadTraffic.NONE);
        Path best = oneSlot.route(network, from, to, fastest).orElseThrow();

        assertThat(oneSlot.alternative(network, from, to, fastest, best)).isPresent();
        NationalEngine noSlot = new NationalEngine(0, Duration.ofSeconds(1));
        assertThat(noSlot.alternative(network, from, to, fastest, best)).isEmpty();
        Thread.currentThread().interrupt();
        try {
            assertThat(oneSlot.alternative(network, from, to, fastest, best)).isEmpty();
        } finally {
            assertThat(Thread.interrupted()).isTrue();
        }
    }

    @Test
    void trafficOnTheNationalGraphDelaysTheRoute() {
        int[] slow = new int[72];
        Arrays.fill(slow, 50);
        CongestionProfile.Builder profile = CongestionProfile.builder();
        Geohash.cover(40.995, 28.995, 41.05, 29.01).forEach(cell -> profile.add(cell, slow));

        RouteResponse free = routes().plan(IN_TOWN, VILLAGE, TravelMode.CAR, Optimization.FASTEST, null);
        RouteResponse jammed = routes(national, profile.build())
                .plan(IN_TOWN, VILLAGE, TravelMode.CAR, Optimization.FASTEST, null);

        assertThat(jammed.durationSeconds()).isCloseTo(2 * free.durationSeconds(), within(2.0));
        assertThat(jammed.trafficDelaySeconds()).isPositive();
    }

    @Test
    void walkingAndCyclingOutsideTheAreaStillUseTheNationwideService() {
        when(osrm.routes(IN_TOWN, VILLAGE, TravelMode.FOOT, 0)).thenReturn(List.of(osrmRoute(IN_TOWN, VILLAGE)));

        RouteResponse route = routes().plan(IN_TOWN, VILLAGE, TravelMode.FOOT, Optimization.FASTEST, null);

        assertThat(route.engine()).isEqualTo("osrm");
        assertThat(route.trafficDelaySeconds()).isNull();
    }

    @Test
    void withoutARoadConnectionTheNationwideServiceIsAsked() {
        when(osrm.routes(VILLAGE, ISLAND, TravelMode.CAR, 0)).thenReturn(List.of(osrmRoute(VILLAGE, ISLAND)));

        assertThat(routes().plan(VILLAGE, ISLAND, TravelMode.CAR, Optimization.FASTEST, null).engine())
                .isEqualTo("osrm");
    }

    @Test
    void withoutARoadConnectionOrTheNationwideServiceThereIsNoRoute() {
        when(external.enabled()).thenReturn(false);

        assertThatThrownBy(() -> routes().plan(VILLAGE, ISLAND, TravelMode.CAR, Optimization.FASTEST, null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("araçla gidilebilecek");
    }

    @Test
    void pointsFarFromAnyCarRoadOrAtTheSameSpotAreRejected() {
        assertThatThrownBy(() -> routes().plan(NOWHERE, VILLAGE, TravelMode.CAR, Optimization.FASTEST, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Başlangıç noktasının 1000 m yakınında araç yolu yok");
        assertThatThrownBy(() -> routes().plan(VILLAGE, GraphFixture.at(50.01, 2), TravelMode.CAR,
                Optimization.FASTEST, null))
                .isInstanceOf(BusinessRuleException.class).hasMessage("Başlangıç ve varış noktası aynı yerde");
    }

    @Test
    void aBusyNationalEngineHandsOverToTheNationwideService() {
        NationalEngine busy = new NationalEngine(0, Duration.ofMillis(10));
        busy.use(region());
        when(osrm.routes(IN_TOWN, VILLAGE, TravelMode.CAR, 0)).thenReturn(List.of(osrmRoute(IN_TOWN, VILLAGE)));

        RouteService routes = routes(busy, CongestionProfile.NONE);

        assertThat(routes.plan(IN_TOWN, VILLAGE, TravelMode.CAR, Optimization.FASTEST, null).engine())
                .isEqualTo("osrm");
        when(external.enabled()).thenReturn(false);
        assertThatThrownBy(() -> routes.plan(IN_TOWN, VILLAGE, TravelMode.CAR, Optimization.FASTEST, null))
                .isInstanceOf(UnavailableException.class);
    }

    @Test
    void withTheNationwideServiceOffCarsStillGetAnywhereOnTheNationalGraph() {
        when(external.enabled()).thenReturn(false);

        assertThat(routes().plan(IN_TOWN, VILLAGE, TravelMode.CAR, Optimization.SHORTEST, null).engine())
                .isEqualTo("national");
        assertThatThrownBy(() -> routes().plan(IN_TOWN, VILLAGE, TravelMode.FOOT, Optimization.FASTEST, null))
                .isInstanceOf(BusinessRuleException.class).hasMessage("Varış noktası harita alanının dışında");
    }

    private static ExternalRoute osrmRoute(GeoPoint from, GeoPoint to) {
        return new ExternalRoute(5_000, 600, List.of(from, to), List.of(
                new Instruction(Maneuver.DEPART, "Ana Cadde", null, 0, 0, 0, 5_000, 600),
                new Instruction(Maneuver.ARRIVE, null, null, 0, 1, 0, 0, 0)),
                new ExternalRoute.Waypoint("Ana Cadde", from, 1), new ExternalRoute.Waypoint(null, to, 1));
    }
}
