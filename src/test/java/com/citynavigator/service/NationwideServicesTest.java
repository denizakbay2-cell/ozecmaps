package com.citynavigator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.citynavigator.dto.PlaceView;
import com.citynavigator.dto.RouteResponse;
import com.citynavigator.entity.Place;
import com.citynavigator.entity.PlaceCategory;
import com.citynavigator.entity.PlaceDetails;
import com.citynavigator.exception.BusinessRuleException;
import com.citynavigator.exception.UnavailableException;
import com.citynavigator.external.ExternalRoute;
import com.citynavigator.external.ExternalServiceException;
import com.citynavigator.external.GeocodedPlace;
import com.citynavigator.external.OsrmClient;
import com.citynavigator.external.PhotonClient;
import com.citynavigator.repository.PlaceRepository;
import com.citynavigator.routing.GeoPoint;
import com.citynavigator.routing.Instruction;
import com.citynavigator.routing.Maneuver;
import com.citynavigator.routing.Optimization;
import com.citynavigator.routing.TravelMode;
import com.citynavigator.testsupport.GraphFixture;
import com.citynavigator.testsupport.Ids;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Routes and searches outside the detailed area, with the public services replaced by mocks.
 */
@ExtendWith(MockitoExtension.class)
class NationwideServicesTest {

    private static final GeoPoint ANKARA = new GeoPoint(39.9334, 32.8597);
    private static final GeoPoint KIZILAY = new GeoPoint(39.9208, 32.8541);
    private static final GeoPoint ATHENS = new GeoPoint(37.98, 23.73);

    @Mock
    private RoutingEngine engine;
    @Mock
    private OsrmClient osrm;
    @Mock
    private PhotonClient photon;
    @Mock
    private PlaceRepository places;
    @Mock
    private ExternalServices external;
    private RouteService routes;
    private PlaceService search;

    @BeforeEach
    void setUp() {
        lenient().when(engine.snapshot()).thenReturn(ServiceFixture.snapshot());
        lenient().when(external.enabled()).thenReturn(true);
        NationalEngine national = new NationalEngine();
        TrafficService traffic = new TrafficService(ServiceFixture.CLOCK,
                org.mockito.Mockito.mock(com.citynavigator.external.TrafficIndexClient.class), external, engine,
                national, com.citynavigator.routing.CongestionProfile.NONE);
        routes = new RouteService(engine, national, traffic, ServiceFixture.CLOCK, osrm, external);
        search = new PlaceService(places, engine, ServiceFixture.CLOCK, photon, external);
    }

    @Test
    void routesOutsideTheAreaComeFromTheNationwideService() {
        when(osrm.routes(ANKARA, KIZILAY, TravelMode.FOOT, 0)).thenReturn(List.of(route(25, 30)));

        RouteResponse route = routes.plan(ANKARA, KIZILAY, TravelMode.FOOT, Optimization.SHORTEST, null);

        assertThat(route.engine()).isEqualTo("osrm");
        assertThat(route.optimize()).isEqualTo("fastest");
        assertThat(route.distanceMeters()).isEqualTo(1500);
        assertThat(route.from().street()).isEqualTo("Atatürk Bulvarı");
        assertThat(route.instructions()).extracting(step -> step.text())
                .containsExactly("Düz devam edin", "Hedefinize ulaştınız");
        assertThat(route.visitedNodes()).isZero();
    }

    @Test
    void theNationwideServicesAlternativeIsPassedOnWhenAskedFor() {
        when(osrm.routes(ANKARA, KIZILAY, TravelMode.CAR, 1)).thenReturn(List.of(route(25, 30), route(25, 30)));

        RouteResponse route = routes.plan(ANKARA, KIZILAY, TravelMode.CAR, Optimization.FASTEST, null, true);

        assertThat(route.alternatives()).singleElement().satisfies(other -> {
            assertThat(other.engine()).isEqualTo("osrm");
            assertThat(other.alternatives()).isEmpty();
        });
    }

    @Test
    void aRouteFromTheAreaToAnotherCityAlsoUsesTheNationwideService() {
        GeoPoint inTown = GraphFixture.at(0, 1);
        when(osrm.routes(inTown, ANKARA, TravelMode.CAR, 0)).thenReturn(List.of(route(5, 5)));
        assertThat(routes.plan(inTown, ANKARA, TravelMode.CAR, Optimization.FASTEST, null).engine()).isEqualTo("osrm");
    }

    @Test
    void refusesPointsAbroadOrFarFromRoadsAndExplainsMissingRoutes() {
        assertThatThrownBy(() -> routes.plan(ATHENS, ANKARA, TravelMode.CAR, Optimization.FASTEST, null))
                .isInstanceOf(BusinessRuleException.class).hasMessage("Başlangıç noktası Türkiye sınırlarının dışında");
        assertThatThrownBy(() -> routes.plan(ANKARA, ATHENS, TravelMode.CAR, Optimization.FASTEST, null))
                .isInstanceOf(BusinessRuleException.class).hasMessage("Varış noktası Türkiye sınırlarının dışında");

        when(osrm.routes(ANKARA, KIZILAY, TravelMode.CAR, 0)).thenReturn(List.of(route(25, 2_500)));
        assertThatThrownBy(() -> routes.plan(ANKARA, KIZILAY, TravelMode.CAR, Optimization.FASTEST, null))
                .isInstanceOf(BusinessRuleException.class).hasMessage("Varış noktasının 1000 m yakınında araç yolu yok");

        when(osrm.routes(KIZILAY, ANKARA, TravelMode.BICYCLE, 0)).thenReturn(List.of());
        assertThatThrownBy(() -> routes.plan(KIZILAY, ANKARA, TravelMode.BICYCLE, Optimization.FASTEST, null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("bisikletle");
        verify(osrm, never()).routes(eq(ATHENS), any(), any(), anyInt());
    }

    @Test
    void anUnreachableServiceIsAServiceUnavailableNotACrash() {
        when(osrm.routes(ANKARA, KIZILAY, TravelMode.CAR, 0)).thenThrow(new ExternalServiceException("timeout"));
        assertThatThrownBy(() -> routes.plan(ANKARA, KIZILAY, TravelMode.CAR, Optimization.FASTEST, null))
                .isInstanceOf(UnavailableException.class).hasMessageContaining("ulaşılamıyor");
    }

    @Test
    void farFromTheAreaNationwideResultsComeFirstAndOwnPlacesOnlyWhenTheyAreWhatWasAskedFor() {
        when(places.findMatching(List.of("migros"), PlaceService.CANDIDATES)).thenReturn(List.of(
                business(1, "Migros", 41.0, 29.0), business(2, "Yeni Migros Jet", 41.0, 29.001)));
        when(photon.search("migros", ANKARA, 5)).thenReturn(List.of(
                new GeocodedPlace("Migros", "node/9", "shop", "supermarket", 39.93, 32.86, "Kızılay, Çankaya, Ankara")));

        List<PlaceView> found = search.search("migros", ANKARA, 5);

        assertThat(found).extracting(PlaceView::name, PlaceView::detail).containsExactly(
                org.assertj.core.groups.Tuple.tuple("Migros", "Kızılay, Çankaya, Ankara"),
                org.assertj.core.groups.Tuple.tuple("Migros", "Ana Cadde"));
        assertThat(found.getFirst().kind()).isEqualTo("Süpermarket");
        assertThat(found.getFirst().id()).isNull();
        assertThat(found.getFirst().business()).isTrue();
    }

    @Test
    void nearTheAreaOwnPlacesComeFirstAndTheSamePlaceIsNotListedTwice() {
        GeoPoint inTown = GraphFixture.at(0, 1);
        when(places.findMatching(List.of("migros"), PlaceService.CANDIDATES)).thenReturn(List.of(
                business(1, "Migros", 41.0, 29.0)));
        when(photon.search("migros", inTown, 5)).thenReturn(List.of(
                new GeocodedPlace("MİGROS", "node/1", "shop", "supermarket", 41.0002, 29.0002, "Kadıköy"),
                new GeocodedPlace("Migros", "node/3", "shop", "supermarket", 41.01, 29.01, "Kadıköy"),
                new GeocodedPlace("Kadıköy", "relation/4", "place", "town", 40.99, 29.02, "İstanbul"),
                new GeocodedPlace("Moda Caddesi", "way/5", "highway", "tertiary", 40.98, 29.02, null)));

        List<PlaceView> found = search.search("migros", inTown, 5);

        assertThat(found).extracting(PlaceView::id).containsExactly(1L, null, null, null);
        assertThat(found).extracting(PlaceView::category).containsExactly("GROCERY", "GROCERY", "SETTLEMENT", "STREET");
    }

    @Test
    void ownResultsStillComeWhenTheGeocoderFailsOrIsOff() {
        when(places.findMatching(List.of("moda"), PlaceService.CANDIDATES)).thenReturn(List.of(
                business(1, "Moda Kahve", 41.0, 29.0)));
        when(photon.search(any(), any(), anyInt())).thenThrow(new ExternalServiceException("down"));
        assertThat(search.search("moda", null, 5)).extracting(PlaceView::name).containsExactly("Moda Kahve");

        when(external.enabled()).thenReturn(false);
        assertThat(search.search("moda", ANKARA, 5)).extracting(PlaceView::name).containsExactly("Moda Kahve");
    }

    @Test
    void businessesAreListedForAStreetLevelViewOnly() {
        when(places.findBusinessesWithin(40.98, 29.02, 40.99, 29.03, PlaceService.MAX_BUSINESSES))
                .thenReturn(List.of(business(7, "Kahve Durağı", 40.985, 29.025)));

        assertThat(search.businessesWithin(40.98, 29.02, 40.99, 29.03)).singleElement()
                .satisfies(view -> {
                    assertThat(view.kind()).isEqualTo("Kafe");
                    assertThat(view.openingHours()).isEqualTo("Mo-Su 08:00-23:00");
                });
        assertThatThrownBy(() -> search.businessesWithin(40.9, 29.0, 41.0, 29.1))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("yakınlaştırın");
        assertThatThrownBy(() -> search.businessesWithin(40.99, 29.02, 40.98, 29.03))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void pointsOutsideTheAreaAreNamedByTheGeocoder() {
        when(photon.reverse(KIZILAY)).thenReturn(Optional.of(
                new GeocodedPlace("Ankara", "node/1", "place", "city", 39.92078, 32.85405, "Çankaya")));
        assertThat(search.streetAt(KIZILAY).street()).isEqualTo("Ankara");

        when(photon.reverse(ANKARA)).thenThrow(new ExternalServiceException("down"));
        assertThatThrownBy(() -> search.streetAt(ANKARA)).isInstanceOf(UnavailableException.class);

        assertThat(search.streetAt(GraphFixture.at(0.3, 0.5)).street()).isEqualTo("Ana Cadde");
        verify(photon, never()).reverse(GraphFixture.at(0.3, 0.5));
    }

    private static ExternalRoute route(double fromSnap, double toSnap) {
        List<GeoPoint> points = List.of(ANKARA, KIZILAY);
        return new ExternalRoute(1500, 1200, points, List.of(
                new Instruction(Maneuver.DEPART, "Atatürk Bulvarı", null, 0, 0, 0, 1500, 1200),
                new Instruction(Maneuver.ARRIVE, null, null, 0, 1, 0, 0, 0)),
                new ExternalRoute.Waypoint("Atatürk Bulvarı", ANKARA, fromSnap),
                new ExternalRoute.Waypoint(null, KIZILAY, toSnap));
    }

    private static Place business(long id, String name, double lat, double lon) {
        boolean cafe = name.contains("Kahve");
        return Ids.assign(new Place(name, cafe ? PlaceCategory.FOOD : PlaceCategory.GROCERY, lat, lon, "node/" + id,
                ServiceFixture.NOW).asBusiness(new PlaceDetails(cafe ? "Kafe" : "Süpermarket",
                cafe ? "Mo-Su 08:00-23:00" : null, null, null)), id);
    }
}
