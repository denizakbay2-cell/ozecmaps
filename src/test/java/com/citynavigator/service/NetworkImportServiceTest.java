package com.citynavigator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.citynavigator.entity.MapArea;
import com.citynavigator.entity.Place;
import com.citynavigator.entity.PlaceCategory;
import com.citynavigator.osm.RoadExtract;
import com.citynavigator.repository.MapAreaRepository;
import com.citynavigator.repository.PlaceRepository;
import com.citynavigator.repository.RoadNetworkRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Runs the import against the real bundled extract; only the database is replaced by mocks.
 */
@ExtendWith(MockitoExtension.class)
class NetworkImportServiceTest {

    @Mock
    private RoadNetworkRepository network;
    @Mock
    private PlaceRepository places;
    @Mock
    private MapAreaRepository areas;
    @Captor
    private ArgumentCaptor<RoadExtract> roads;
    @Captor
    private ArgumentCaptor<List<Place>> importedPlaces;
    @Captor
    private ArgumentCaptor<MapArea> area;
    private NetworkImportService service;

    @BeforeEach
    void setUp() {
        service = new NetworkImportService(network, places, areas, ServiceFixture.CLOCK);
    }

    @Test
    void leavesAnExistingNetworkAlone() {
        when(network.isEmpty()).thenReturn(false);
        assertThat(service.importBundledAreaIfEmpty()).isFalse();
        verify(network, never()).insert(any());
        verify(places, never()).addAll(any());
    }

    @Test
    void importsTheBundledKadikoyExtractIntoAnEmptyDatabase() {
        when(network.isEmpty()).thenReturn(true);

        assertThat(service.importBundledAreaIfEmpty()).isTrue();

        verify(network).insert(roads.capture());
        assertThat(roads.getValue().ways()).hasSizeGreaterThan(10_000);
        verify(places).addAll(importedPlaces.capture());
        List<Place> stored = importedPlaces.getValue();
        assertThat(stored).hasSizeGreaterThan(2_000);
        assertThat(stored).anySatisfy(place -> {
            assertThat(place.getName()).isEqualTo("Moda");
            assertThat(place.getCategory()).isEqualTo(PlaceCategory.NEIGHBOURHOOD);
        });
        assertThat(stored).anySatisfy(place -> assertThat(place.getName()).isEqualTo("Bağdat Caddesi"));
        assertThat(stored.stream().filter(Place::isBusiness).count()).isGreaterThan(5_000);
        // pharmacies are in both extracts; the business version, with its details, is the one kept
        assertThat(stored).filteredOn(place -> place.getCategory() == PlaceCategory.HEALTH
                && "Eczane".equals(place.getDetails().kind())).allSatisfy(place ->
                assertThat(place.isBusiness()).isTrue()).hasSizeGreaterThan(400);
        Set<String> refs = new HashSet<>();
        assertThat(stored).allSatisfy(place -> assertThat(refs.add(place.getOsmRef())).isTrue());

        verify(areas).save(area.capture());
        assertThat(area.getValue().getName()).isEqualTo("Kadıköy ve çevresi, İstanbul");
        assertThat(area.getValue().getCenter()).containsExactly(40.9876, 29.0290);
        assertThat(area.getValue().getDataTimestamp()).isPresent();
        double[] bounds = area.getValue().getBounds();
        assertThat(bounds[0]).isLessThan(40.9876);
        assertThat(bounds[3]).isGreaterThan(29.0290);
    }
}
