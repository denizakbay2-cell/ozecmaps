package com.citynavigator.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.citynavigator.dto.IncidentRequest;
import com.citynavigator.dto.IncidentView;
import com.citynavigator.dto.MapInfo;
import com.citynavigator.dto.PlaceRequest;
import com.citynavigator.dto.PingResponse;
import com.citynavigator.dto.PlaceView;
import com.citynavigator.dto.RouteResponse;
import com.citynavigator.dto.TrafficStatus;
import com.citynavigator.routing.GeoPoint;
import com.citynavigator.routing.Optimization;
import com.citynavigator.routing.RoadsidePlace;
import com.citynavigator.routing.TravelMode;
import com.citynavigator.service.AlongRouteService;
import com.citynavigator.service.IncidentService;
import com.citynavigator.service.MapService;
import com.citynavigator.service.PlaceService;
import com.citynavigator.service.RouteService;
import com.citynavigator.service.RoutingEngine;
import com.citynavigator.service.TrafficService;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResourcesTest {

    @Mock
    private RouteService routes;
    @Mock
    private PlaceService places;
    @Mock
    private AlongRouteService along;
    @Mock
    private IncidentService incidents;
    @Mock
    private MapService maps;
    @Mock
    private TrafficService traffic;
    @Mock
    private UriInfo uriInfo;

    @Test
    void routeParsesCoordinatesModeAndOptimisation() {
        RouteResponse planned = new RouteResponse("foot", "shortest", "astar", 1, 1, null, 0L, null, null,
                List.of(), List.of(), List.of(), List.of(), 1, List.of());
        when(routes.plan(new GeoPoint(40.99, 29.02), List.of(), new GeoPoint(40.98, 29.03), TravelMode.FOOT,
                Optimization.SHORTEST, null, false)).thenReturn(planned);
        Instant evening = Instant.parse("2026-11-14T15:00:00Z");
        when(routes.plan(new GeoPoint(40.99, 29.02), List.of(new GeoPoint(40.985, 29.025), new GeoPoint(40.97, 29.04)),
                new GeoPoint(40.98, 29.03), TravelMode.CAR, Optimization.FASTEST, evening, true)).thenReturn(planned);

        RouteResource resource = new RouteResource(routes);
        assertThat(resource.route(" 40.99, 29.02", "40.98,29.03", "foot", "shortest", null, "false", null))
                .isSameAs(planned);
        assertThat(resource.route("40.99,29.02", "40.98,29.03", "car", "fastest", "2026-11-14T18:00+03:00", "true",
                "40.985,29.025; 40.97,29.04")).isSameAs(planned);
        assertThat("40.1,29.1;40.2,29.2;40.3,29.3".matches(RouteResource.STOPS_PATTERN)).isTrue();
        assertThat("40.1,29.1;40.2,29.2;40.3,29.3;40.4,29.4".matches(RouteResource.STOPS_PATTERN)).isFalse();
    }

    @Test
    void departureTimesNeedAnOffset() {
        assertThat(RouteResource.parseDeparture("2026-11-14T15:00:00.000Z"))
                .isEqualTo(Instant.parse("2026-11-14T15:00:00Z"));
        assertThat(RouteResource.parseDeparture(null)).isNull();
        assertThat("2026-11-14T18:00+03:00".matches(RouteResource.DEPARTURE_PATTERN)).isTrue();
        assertThat("2026-11-14T18:00:30.5Z".matches(RouteResource.DEPARTURE_PATTERN)).isTrue();
        assertThat("2026-11-14T18:00".matches(RouteResource.DEPARTURE_PATTERN)).isFalse();
        assertThat("yarın akşam".matches(RouteResource.DEPARTURE_PATTERN)).isFalse();
        assertThatThrownBy(() -> RouteResource.parseDeparture("2026-13-45T18:00Z"))
                .isInstanceOf(BadRequestException.class).hasMessage("departAt: geçerli bir zaman değil");
    }

    @Test
    void trafficStatusAndRoadsComeFromTheTrafficService() {
        TrafficStatus status = new TrafficStatus(72, 60.0, "çok yoğun", "normalden yoğun", null, 2395, "İBB");
        when(traffic.status()).thenReturn(status);
        TrafficResource resource = new TrafficResource(traffic);

        assertThat(resource.status()).isSameAs(status);
        resource.roads(40.98, 29.02, 41.0, 29.05);
        verify(traffic).roadsWithin(40.98, 29.02, 41.0, 29.05);
    }

    @Test
    void coordinatesOutOfRangeAreABadRequest() {
        RouteResource resource = new RouteResource(routes);
        assertThatThrownBy(() -> resource.route("95,29", "40.98,29.03", "car", "fastest", null, "false", null))
                .isInstanceOf(BadRequestException.class).hasMessage("from: geçerli bir koordinat değil");
        assertThat("40.9903,29.0230".matches(Coordinates.PATTERN)).isTrue();
        assertThat("-1.5, -170".matches(Coordinates.PATTERN)).isTrue();
        assertThat("40.99;29.02".matches(Coordinates.PATTERN)).isFalse();
        assertThat("Kadıköy".matches(Coordinates.PATTERN)).isFalse();
        assertThatThrownBy(() -> Coordinates.parse("40.99", "to")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void placesAnswerSearchesAndCreateWithALocation() {
        PlaceView view = new PlaceView(12L, "Okul", "PARK", "Park ve spor", null, null, 41, 29, false, false, null,
                null, null);
        when(places.search("moda", new GeoPoint(40.98, 29.03), 5)).thenReturn(List.of(view));
        when(places.search("moda", null, 5)).thenReturn(List.of());
        when(places.add(any())).thenReturn(view);
        when(uriInfo.getAbsolutePathBuilder()).thenReturn(UriBuilder.fromUri("http://localhost/api/places"));
        PlaceResource resource = new PlaceResource(places, along);

        assertThat(resource.search("moda", 5, 40.98, 29.03)).containsExactly(view);
        assertThat(resource.search("moda", 5, 40.98, null)).isEmpty();
        Response created = resource.add(new PlaceRequest("Okul", "PARK", 41.0, 29.0), uriInfo);
        assertThat(created.getStatus()).isEqualTo(201);
        assertThat(created.getLocation()).isEqualTo(URI.create("http://localhost/api/places/12"));
        assertThat(resource.delete(12).getStatus()).isEqualTo(204);
        verify(places).delete(12);
        resource.streetAt(41.0, 29.0);
        verify(places).streetAt(new GeoPoint(41, 29));
        resource.get(12);
        verify(places).get(12);
        resource.along("pharmacy", "foot", "_ulyF_oxpD");
        verify(along).along(RoadsidePlace.Kind.PHARMACY, TravelMode.FOOT, "_ulyF_oxpD");
    }

    @Test
    void incidentsAreReportedListedAndResolved() {
        IncidentView view = new IncidentView(3, "CLOSURE", "Kapalı yol", "ACTIVE", null, null, 41, 29, null, null,
                List.of());
        when(incidents.report(any())).thenReturn(view);
        when(incidents.active()).thenReturn(List.of(view));
        when(uriInfo.getAbsolutePathBuilder()).thenReturn(UriBuilder.fromUri("http://localhost/api/incidents"));
        IncidentResource resource = new IncidentResource(incidents);

        Response created = resource.report(new IncidentRequest("CLOSURE", null, 41.0, 29.0, null), uriInfo);
        assertThat(created.getStatus()).isEqualTo(201);
        assertThat(created.getLocation()).hasPath("/api/incidents/3");
        assertThat(resource.active()).containsExactly(view);
        resource.resolve(3);
        verify(incidents).resolve(3);
        resource.get(3);
        verify(incidents).get(3);
    }

    @Test
    void businessesComeFromThePlaceService() {
        new BusinessResource(places).within(40.98, 29.02, 40.99, 29.03);
        verify(places).businessesWithin(40.98, 29.02, 40.99, 29.03);
    }

    @Test
    void mapAndPingAnswer() {
        MapInfo info = new MapInfo("Kadıköy", List.of(), List.of(), 1, 1, 1, null, null, "OSM", true, List.of(),
                null, 0);
        when(maps.info()).thenReturn(info);
        assertThat(new MapResource(maps).info()).isSameAs(info);
        RoutingEngine engine = org.mockito.Mockito.mock(RoutingEngine.class);
        when(engine.isLoaded()).thenReturn(false, true);
        assertThat(new PingResource(engine).ping().getStatus()).isEqualTo(503);
        Response up = new PingResource(engine).ping();
        assertThat(up.getStatus()).isEqualTo(200);
        assertThat(((PingResponse) up.getEntity()).status()).isEqualTo("UP");
    }
}
