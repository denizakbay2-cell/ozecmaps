package com.citynavigator.resource;

import com.citynavigator.dto.RouteResponse;
import com.citynavigator.routing.GeoPoint;
import com.citynavigator.routing.Optimization;
import com.citynavigator.routing.TravelMode;
import com.citynavigator.service.RouteService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * {@code /api/route?from=lat,lon&to=lat,lon&via=lat,lon;lat,lon&mode=car|bicycle|foot&optimize=fastest|shortest&departAt=2026-11-14T18:00+03:00&alternatives=true}.
 * A GET, because planning a route changes nothing and the URL describes the route completely. Without
 * {@code departAt} the trip starts now; {@code via} names up to three stops, in order; with
 * {@code alternatives=true} a route without stops also carries a second, clearly different route if there is a good
 * one.
 */
@Path("route")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class RouteResource {

    /** Date, time to the minute or finer, and an offset: what {@code Date.toISOString()} produces, among others. */
    /** One to three coordinates separated by semicolons. */
    static final String STOPS_PATTERN = Coordinates.PATTERN + "(;" + Coordinates.PATTERN + "){0,2}";

    static final String DEPARTURE_PATTERN =
            "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2}(\\.\\d{1,9})?)?(Z|[+-]\\d{2}:\\d{2})";

    private RouteService routes;

    protected RouteResource() {
        // for the CDI proxy
    }

    @Inject
    public RouteResource(RouteService routes) {
        this.routes = routes;
    }

    @GET
    public RouteResponse route(
            @QueryParam("from") @NotNull(message = "gerekli")
            @Pattern(regexp = Coordinates.PATTERN, message = Coordinates.MESSAGE) String from,
            @QueryParam("to") @NotNull(message = "gerekli")
            @Pattern(regexp = Coordinates.PATTERN, message = Coordinates.MESSAGE) String to,
            @QueryParam("mode") @DefaultValue("car")
            @Pattern(regexp = "car|bicycle|foot", message = "car, bicycle ya da foot olmalı") String mode,
            @QueryParam("optimize") @DefaultValue("fastest")
            @Pattern(regexp = "fastest|shortest", message = "fastest ya da shortest olmalı") String optimize,
            @QueryParam("departAt")
            @Pattern(regexp = DEPARTURE_PATTERN, message = "saat dilimiyle birlikte ISO-8601 zamanı olmalı, örneğin "
                    + "2026-11-14T18:00+03:00") String departAt,
            @QueryParam("alternatives") @DefaultValue("false")
            @Pattern(regexp = "true|false", message = "true ya da false olmalı") String alternatives,
            @QueryParam("via")
            @Pattern(regexp = STOPS_PATTERN, message = "en fazla 3 nokta, enlem,boylam;enlem,boylam biçiminde olmalı")
            String via) {
        return routes.plan(Coordinates.parse(from, "from"), parseStops(via), Coordinates.parse(to, "to"),
                TravelMode.valueOf(mode.toUpperCase(Locale.ROOT)),
                Optimization.valueOf(optimize.toUpperCase(Locale.ROOT)), parseDeparture(departAt),
                Boolean.parseBoolean(alternatives));
    }

    static List<GeoPoint> parseStops(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(";")).map(stop -> Coordinates.parse(stop, "via")).toList();
    }

    static Instant parseDeparture(String value) {
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException e) {
            throw new BadRequestException("departAt: geçerli bir zaman değil", e);
        }
    }
}
