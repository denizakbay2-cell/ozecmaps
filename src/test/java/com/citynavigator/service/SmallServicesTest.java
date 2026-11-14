package com.citynavigator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.citynavigator.dto.MapInfo;
import com.citynavigator.entity.MapArea;
import com.citynavigator.repository.MapAreaRepository;
import com.citynavigator.routing.Instruction;
import com.citynavigator.routing.Maneuver;
import com.citynavigator.routing.RoadClass;
import com.citynavigator.routing.TravelMode;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The small pieces of the service layer: wording, the map description, the area file and the demo data.
 */
class SmallServicesTest {

    @Test
    void wordsDirectionsInTurkish() {
        // never a compass direction: the start is simply straight on, whichever way the road runs
        assertThat(Phrases.instruction(step(Maneuver.DEPART, 0, 44))).isEqualTo("Düz devam edin");
        assertThat(Phrases.instruction(step(Maneuver.DEPART, 0, 225))).isEqualTo("Düz devam edin");
        assertThat(Phrases.instruction(step(Maneuver.ROUNDABOUT, 3, 0))).isEqualTo("Dönel kavşakta 3. çıkışa girin");
        assertThat(Phrases.instruction(step(Maneuver.ROUNDABOUT, 0, 0))).isEqualTo("Dönel kavşaktan devam edin");
        assertThat(Phrases.instruction(step(Maneuver.SHARP_RIGHT, 0, 0))).isEqualTo("Keskin sağa dönün");
        assertThat(Phrases.instruction(step(Maneuver.U_TURN, 0, 0))).isEqualTo("U dönüşü yapın");
        for (Maneuver maneuver : Maneuver.values()) {
            assertThat(Phrases.instruction(step(maneuver, 1, 0))).isNotBlank();
        }
    }

    @Test
    void describesNamelessRoadsByTheirKind() {
        assertThat(Phrases.streetLabel("Moda Caddesi", RoadClass.FOOTWAY)).isEqualTo("Moda Caddesi");
        assertThat(Phrases.streetLabel(null, RoadClass.STEPS)).isEqualTo("merdiven");
        assertThat(Phrases.streetLabel(null, RoadClass.PRIMARY_LINK)).isEqualTo("bağlantı yolu");
        assertThat(Phrases.streetLabel(null, RoadClass.RESIDENTIAL)).isNull();
        assertThat(Phrases.streetLabel(null, null)).isNull();
        assertThat(Phrases.roadFor(TravelMode.FOOT)).isEqualTo("yürünebilir yol");
        assertThat(Phrases.by(TravelMode.CAR)).isEqualTo("araçla");
    }

    @Test
    void describesTheMapForTheClient() {
        MapAreaRepository areas = mock(MapAreaRepository.class);
        RoutingEngine engine = mock(RoutingEngine.class);
        when(engine.snapshot()).thenReturn(ServiceFixture.snapshot());
        when(areas.find()).thenReturn(Optional.of(new MapArea("Kasaba", new double[] {40.9, 28.9, 41.1, 29.1},
                new double[] {41, 29}, ServiceFixture.NOW, ServiceFixture.NOW)));

        ExternalServices external = mock(ExternalServices.class);
        when(external.enabled()).thenReturn(true);
        NationalEngine national = new NationalEngine();
        TrafficService traffic = mock(TrafficService.class);
        when(traffic.profile()).thenReturn(com.citynavigator.routing.CongestionProfile.NONE);
        MapInfo info = new MapService(areas, engine, external, national, traffic).info();

        assertThat(info.name()).isEqualTo("Kasaba");
        assertThat(info.bounds()).containsExactly(40.9, 28.9, 41.1, 29.1);
        assertThat(info.center()).containsExactly(41.0, 29.0);
        assertThat(info.nodes()).isEqualTo(8);
        assertThat(info.attribution()).contains("OpenStreetMap");
        assertThat(info.nationwide()).isTrue();
        assertThat(info.nationwideBounds()).containsExactly(35.8, 25.6, 42.2, 44.9);
        assertThat(info.nationalGraph()).isNull();
        assertThat(info.trafficCells()).isZero();

        when(areas.find()).thenReturn(Optional.empty());
        MapService empty = new MapService(areas, engine, external, national, traffic);
        assertThatThrownBy(empty::info).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void readsTheAreaFileAsUtf8() {
        AreaDefinition area = AreaDefinition.read(stream("name = Kadıköy ve çevresi\ncenter = 40.98, 29.03\n"));
        assertThat(area.name()).isEqualTo("Kadıköy ve çevresi");
        assertThat(area.centerLat()).isEqualTo(40.98);
        assertThat(area.centerLon()).isEqualTo(29.03);
        assertThatThrownBy(() -> AreaDefinition.read(stream("name=X\n"))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void addsDemoIncidentsOnlyToADatabaseWithoutAny() {
        IncidentService incidents = mock(IncidentService.class);
        when(incidents.anyRecorded()).thenReturn(true, false);
        DemoIncidents demo = new DemoIncidents(incidents);

        demo.addIfNone();
        verify(incidents, never()).report(any());
        demo.addIfNone();
        verify(incidents, times(3)).report(any());
    }

    private static Instruction step(Maneuver maneuver, int exit, double bearing) {
        return new Instruction(maneuver, null, null, exit, 0, bearing, 0, 0);
    }

    private static ByteArrayInputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }
}
