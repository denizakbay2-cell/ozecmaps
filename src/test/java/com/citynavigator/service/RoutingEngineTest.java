package com.citynavigator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.citynavigator.entity.Incident;
import com.citynavigator.entity.IncidentType;
import com.citynavigator.entity.SegmentRef;
import com.citynavigator.repository.IncidentRepository;
import com.citynavigator.repository.RoadNetworkRepository;
import com.citynavigator.routing.RoadGraph;
import com.citynavigator.routing.TrafficConditions;
import com.citynavigator.routing.TravelMode;
import com.citynavigator.testsupport.Ids;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoutingEngineTest {

    @Mock
    private RoadNetworkRepository network;
    @Mock
    private IncidentRepository incidents;
    private RoutingEngine engine;
    private final RoadGraph graph = ServiceFixture.town();

    @BeforeEach
    void setUp() {
        engine = new RoutingEngine(network, incidents, ServiceFixture.CLOCK);
    }

    @Test
    void refusesToRouteBeforeTheNetworkIsLoaded() {
        assertThat(engine.isLoaded()).isFalse();
        assertThatThrownBy(engine::snapshot).isInstanceOf(IllegalStateException.class);
        engine.apply(IncidentChanged.resolved(1));
        assertThatThrownBy(engine::snapshot).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loadsTheGraphWithTheActiveIncidents() {
        Incident closure = Ids.assign(Incident.report(IncidentType.CLOSURE, null, new double[] {41, 29}, null,
                Set.of(new SegmentRef(10, 0), new SegmentRef(77, 3)), ServiceFixture.NOW, Duration.ofHours(1)), 3L);
        when(network.loadGraph()).thenReturn(graph);
        when(incidents.findActive(ServiceFixture.NOW)).thenReturn(List.of(closure));

        engine.load();

        assertThat(engine.isLoaded()).isTrue();
        TrafficConditions traffic = engine.snapshot().traffic();
        assertThat(engine.snapshot().graph()).isSameAs(graph);
        assertThat(traffic.speedFactor(graph.segment(10, 0), TravelMode.CAR, ServiceFixture.NOW)).isZero();
        assertThat(traffic.speedFactor(graph.segment(10, 0), TravelMode.CAR,
                ServiceFixture.NOW.plus(Duration.ofHours(1)))).isEqualTo(1);
        assertThat(traffic.restrictedSegmentCount()).isEqualTo(1);
    }

    @Test
    void appliesReportedAndResolvedIncidentsWithoutTouchingTheGraph() {
        when(network.loadGraph()).thenReturn(graph);
        engine.load();
        RoutingEngine.Snapshot before = engine.snapshot();
        int segment = graph.segment(10, 1);

        engine.onIncidentChanged(new IncidentChanged(4, TrafficConditions.Restriction.slowdown(4, 0.5, null),
                List.of(segment)));
        assertThat(engine.snapshot().traffic().speedFactor(segment, TravelMode.CAR, ServiceFixture.NOW))
                .isEqualTo(0.5);
        assertThat(engine.snapshot().router()).isSameAs(before.router());

        engine.onIncidentChanged(IncidentChanged.resolved(4));
        assertThat(engine.snapshot().traffic().restrictedSegmentCount()).isZero();
        assertThat(before.traffic().restrictedSegmentCount()).isZero();
    }

    @Test
    void turnsIncidentTypesIntoRestrictions() {
        Incident accident = Ids.assign(Incident.report(IncidentType.ACCIDENT, null, new double[] {41, 29}, null,
                Set.of(new SegmentRef(10, 0)), ServiceFixture.NOW, null), 5L);
        TrafficConditions.Restriction restriction = RoutingEngine.restrictionOf(accident);
        assertThat(restriction.closed()).isFalse();
        assertThat(restriction.speedFactor()).isEqualTo(IncidentType.ACCIDENT.speedFactor());
        assertThat(restriction.until()).isNull();
        assertThat(restriction.incidentId()).isEqualTo(5);
    }
}
