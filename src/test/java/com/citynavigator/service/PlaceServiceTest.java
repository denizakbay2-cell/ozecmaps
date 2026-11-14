package com.citynavigator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.citynavigator.dto.PlaceRequest;
import com.citynavigator.dto.PlaceView;
import com.citynavigator.dto.StreetView;
import com.citynavigator.entity.Place;
import com.citynavigator.entity.PlaceCategory;
import com.citynavigator.exception.BusinessRuleException;
import com.citynavigator.exception.ConflictException;
import com.citynavigator.exception.ResourceNotFoundException;
import com.citynavigator.external.PhotonClient;
import com.citynavigator.repository.PlaceRepository;
import com.citynavigator.routing.GeoPoint;
import com.citynavigator.testsupport.GraphFixture;
import com.citynavigator.testsupport.Ids;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlaceServiceTest {

    @Mock
    private PlaceRepository places;
    @Mock
    private RoutingEngine engine;
    @Mock
    private PhotonClient nationwide;
    @Mock
    private ExternalServices external;
    private PlaceService service;

    @BeforeEach
    void setUp() {
        lenient().when(engine.snapshot()).thenReturn(ServiceFixture.snapshot());
        service = new PlaceService(places, engine, ServiceFixture.CLOCK, nationwide, external);
    }

    @Test
    void searchesWithFoldedWordsAndRanksTheBestMatchesFirst() {
        when(places.findMatching(List.of("moda", "c"), PlaceService.CANDIDATES)).thenReturn(List.of(
                place(1, "Yukarı Moda Camii", PlaceCategory.WORSHIP),
                place(2, "Moda Caddesi", PlaceCategory.STREET),
                place(3, "Moda Camlık Eczanesi", PlaceCategory.HEALTH),
                place(4, "Moda Cami Durağı", PlaceCategory.TRANSIT)));

        List<PlaceView> found = service.search("MODA C.", null, 3);

        assertThat(found).extracting(PlaceView::name)
                .containsExactly("Moda Cami Durağı", "Moda Caddesi", "Moda Camlık Eczanesi");
        assertThat(found.getFirst().categoryLabel()).isEqualTo("Ulaşım");
    }

    @Test
    void tellsTheBranchesOfAChainApartAndPutsTheNearestFirst() {
        when(places.findMatching(List.of("migros"), PlaceService.CANDIDATES)).thenReturn(List.of(
                place(1, "Migros", PlaceCategory.GROCERY, GraphFixture.at(0.3, 0.2)),
                place(2, "Migros", PlaceCategory.GROCERY, GraphFixture.at(0.6, 1.3)),
                place(3, "Migros", PlaceCategory.GROCERY, GraphFixture.at(5.2, 5.5)),
                place(4, "Migros", PlaceCategory.GROCERY, GraphFixture.at(30, 30))));
        when(places.findInCategories(List.of(PlaceCategory.NEIGHBOURHOOD))).thenReturn(List.of(
                place(10, "Moda", PlaceCategory.NEIGHBOURHOOD, GraphFixture.at(0.5, 0)),
                place(11, "Caferağa", PlaceCategory.NEIGHBOURHOOD, GraphFixture.at(0.5, 2))));

        List<PlaceView> found = service.search("migros", GraphFixture.at(5, 5), 8);

        // the street within 80 m and the nearest neighbourhood within 2 km; the lone branch far away has neither
        assertThat(found).extracting(PlaceView::id, PlaceView::detail).containsExactly(
                tuple(3L, "Ada Yolu, Caferağa"),
                tuple(2L, "Yan Sokak, Caferağa"),
                tuple(1L, "Ana Cadde, Moda"),
                tuple(4L, null));
    }

    @Test
    void neighbourhoodsAndStreetsAreNotDescribedByThemselves() {
        when(places.findMatching(List.of("moda"), PlaceService.CANDIDATES)).thenReturn(List.of(
                place(10, "Moda", PlaceCategory.NEIGHBOURHOOD, GraphFixture.at(0.5, 0)),
                place(12, "Moda Caddesi", PlaceCategory.STREET, GraphFixture.at(0, 0.5))));
        when(places.findInCategories(List.of(PlaceCategory.NEIGHBOURHOOD))).thenReturn(List.of(
                place(10, "Moda", PlaceCategory.NEIGHBOURHOOD, GraphFixture.at(0.5, 0))));

        assertThat(service.search("moda", null, 8)).extracting(PlaceView::name, PlaceView::detail).containsExactly(
                tuple("Moda", null), tuple("Moda Caddesi", null));
    }

    @Test
    void ignoresQueriesTooShortToMeanAnything() {
        assertThat(service.search(" k ", null, 8)).isEmpty();
        assertThat(service.search("...", null, 8)).isEmpty();
        verifyNoInteractions(places);
    }

    @Test
    void namesTheStreetAtAPoint() {
        StreetView street = service.streetAt(GraphFixture.at(0.3, 0.5));
        assertThat(street.street()).isEqualTo("Ana Cadde");
        assertThat(street.distanceMeters()).isEqualTo(30);
    }

    @Test
    void fallsBackToANamelessRoadAndFailsFarFromAnyRoad() {
        assertThat(service.streetAt(GraphFixture.at(-2.1, 0.5)).street()).isNull();
        assertThatThrownBy(() -> service.streetAt(GraphFixture.at(-4, 3)))
                .isInstanceOf(BusinessRuleException.class).hasMessage("Bu noktanın yakınında yol yok");
    }

    @Test
    void addsAPlaceInsideTheMap() {
        when(places.add(any())).thenAnswer(call -> Ids.assign(call.getArgument(0), 50L));

        PlaceView added = service.add(new PlaceRequest("Okul Bahçesi", "PARK", 41.0, 29.0));

        assertThat(added.id()).isEqualTo(50);
        assertThat(added.fromOpenStreetMap()).isFalse();
        verify(places).existsNear(eq("okul bahcesi"), eq(41.0), eq(29.0), anyDouble(), anyDouble());
    }

    @Test
    void refusesADuplicateNearbyAndAPlaceOffTheMap() {
        when(places.existsNear(eq("okul bahcesi"), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(true);
        assertThatThrownBy(() -> service.add(new PlaceRequest("Okul Bahçesi", "PARK", 41.0, 29.0)))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.add(new PlaceRequest("Anıtkabir", "CULTURE", 39.92, 32.83)))
                .isInstanceOf(BusinessRuleException.class).hasMessage("Yer harita alanının içinde olmalı");
        verify(places, never()).add(any());
    }

    @Test
    void deletesOnlyPlacesAddedByHand() {
        Place imported = Ids.assign(new Place("Moda", PlaceCategory.NEIGHBOURHOOD, 41, 29, "node/6",
                ServiceFixture.NOW), 6L);
        Place added = place(7, "Okul Bahçesi", PlaceCategory.PARK);
        when(places.findById(6)).thenReturn(Optional.of(imported));
        when(places.findById(7)).thenReturn(Optional.of(added));

        assertThatThrownBy(() -> service.delete(6)).isInstanceOf(BusinessRuleException.class);
        service.delete(7);

        verify(places).remove(added);
        verify(places, never()).remove(imported);
    }

    @Test
    void reportsUnknownPlaces() {
        when(places.findById(anyLong())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(99))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Yer bulunamadı (no 99)");
    }

    private static Place place(long id, String name, PlaceCategory category) {
        return Ids.assign(new Place(name, category, 41, 29, null, ServiceFixture.NOW), id);
    }

    private static Place place(long id, String name, PlaceCategory category, GeoPoint point) {
        return Ids.assign(new Place(name, category, point.lat(), point.lon(), "node/" + id, ServiceFixture.NOW), id);
    }
}
