package com.citynavigator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.citynavigator.dto.IncidentRequest;
import com.citynavigator.dto.IncidentView;
import com.citynavigator.entity.Incident;
import com.citynavigator.entity.IncidentStatus;
import com.citynavigator.entity.IncidentType;
import com.citynavigator.entity.SegmentRef;
import com.citynavigator.exception.BusinessRuleException;
import com.citynavigator.exception.ResourceNotFoundException;
import com.citynavigator.repository.IncidentRepository;
import com.citynavigator.routing.RoadGraph;
import com.citynavigator.testsupport.GraphFixture;
import com.citynavigator.testsupport.Ids;
import jakarta.enterprise.event.Event;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

    @Mock
    private IncidentRepository incidents;
    @Mock
    private RoutingEngine engine;
    @Mock
    private Event<IncidentChanged> changes;
    @Captor
    private ArgumentCaptor<Incident> stored;
    @Captor
    private ArgumentCaptor<IncidentChanged> fired;
    private IncidentService service;
    private final RoutingEngine.Snapshot snapshot = ServiceFixture.snapshot();
    private final RoadGraph graph = snapshot.graph();

    @BeforeEach
    void setUp() {
        when(engine.snapshot()).thenReturn(snapshot);
        service = new IncidentService(incidents, engine, changes, ServiceFixture.CLOCK);
    }

    @Test
    void aClosureCoversTheBlockAndReachesTheEngineThroughAnEvent() {
        when(incidents.add(stored.capture())).thenAnswer(call -> Ids.assign(call.getArgument(0), 5L));

        IncidentView view = service.report(new IncidentRequest("CLOSURE", "Asfalt", 41.0001, 29.0006, 60));

        Incident incident = stored.getValue();
        assertThat(incident.getType()).isEqualTo(IncidentType.CLOSURE);
        assertThat(incident.getStreet()).isEqualTo("Ana Cadde");
        assertThat(incident.getEndsAt()).contains(ServiceFixture.NOW.plus(Duration.ofHours(1)));
        // node 2 is a junction, so only the block 1-2 closes
        assertThat(incident.getSegments()).containsExactly(new SegmentRef(10, 0));
        verify(changes).fire(fired.capture());
        assertThat(fired.getValue().incidentId()).isEqualTo(5);
        assertThat(fired.getValue().restriction().closed()).isTrue();
        assertThat(fired.getValue().segments()).containsExactly(graph.segment(10, 0));
        assertThat(view.lines()).hasSize(1);
        assertThat(view.typeLabel()).isEqualTo("Kapalı yol");
    }

    @Test
    void roadworksSlowCarsDownAlongTheRoad() {
        when(incidents.add(stored.capture())).thenAnswer(call -> Ids.assign(call.getArgument(0), 6L));

        service.report(new IncidentRequest("ROADWORK", null, 41.0, 29.0012, null));

        assertThat(stored.getValue().getSegments())
                .containsExactlyInAnyOrder(new SegmentRef(10, 0), new SegmentRef(10, 1));
        assertThat(stored.getValue().getEndsAt()).isEmpty();
        verify(changes).fire(fired.capture());
        assertThat(fired.getValue().restriction().closed()).isFalse();
        assertThat(fired.getValue().restriction().speedFactor()).isEqualTo(IncidentType.ROADWORK.speedFactor());
    }

    @Test
    void mustBeReportedOnACarRoadInsideTheMap() {
        assertThatThrownBy(() -> service.report(new IncidentRequest("ACCIDENT", null,
                GraphFixture.at(-2, 1).lat(), GraphFixture.at(-2, 1).lon(), null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("araç yolu");
        assertThatThrownBy(() -> service.report(new IncidentRequest("ACCIDENT", null, 39.9, 32.8, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("ayrıntılı yol haritası olan bölgede");
        verify(incidents, never()).add(any());
        verify(changes, never()).fire(any());
    }

    @Test
    void resolvingWritesAtOnceAndTellsTheEngine() {
        Incident incident = Ids.assign(Incident.report(IncidentType.ACCIDENT, null, new double[] {41, 29}, null,
                Set.of(new SegmentRef(10, 0)), ServiceFixture.NOW, null), 8L);
        when(incidents.findById(8)).thenReturn(Optional.of(incident));

        IncidentView view = service.resolve(8);

        assertThat(view.status()).isEqualTo(IncidentStatus.RESOLVED.name());
        verify(incidents).flush();
        verify(changes).fire(IncidentChanged.resolved(8));
    }

    @Test
    void listsActiveIncidentsAndReportsUnknownOnes() {
        Incident incident = Ids.assign(Incident.report(IncidentType.CONGESTION, null, new double[] {41, 29}, null,
                Set.of(new SegmentRef(10, 0), new SegmentRef(99, 0)), ServiceFixture.NOW, null), 9L);
        when(incidents.findActive(ServiceFixture.NOW)).thenReturn(List.of(incident));
        when(incidents.findById(10)).thenReturn(Optional.empty());

        assertThat(service.active()).singleElement().satisfies(view -> {
            assertThat(view.id()).isEqualTo(9);
            assertThat(view.lines()).hasSize(1);
        });
        assertThatThrownBy(() -> service.get(10)).isInstanceOf(ResourceNotFoundException.class);
    }
}
