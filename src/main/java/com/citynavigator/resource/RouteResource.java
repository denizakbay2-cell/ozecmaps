package com.citynavigator.resource;

import com.citynavigator.dto.RouteResponse;
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
import java.util.Locale;

/**
 * {@code /api/route?from=lat,lon&to=lat,lon&mode=car|bicycle|foot&optimize=fastest|shortest&departAt=2026-11-14T18:00+03:00&alternatives=true}.
 * A GET, because planning a route changes nothing and the URL describes the route completely. Without
 * {@code departAt} the trip starts now; with {@code alternatives=true} the response also carries a second, clearly
 * different route if there is a good one.
 */
@Path("route")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class RouteResource {

    /** Date, time to the minute or finer, and an offset: what {@code Date.toISOString()} produces, among others. */
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
            @Pattern(regexp = "true|false", message = "true ya da false olmalı") String alternatives) {
        return routes.plan(Coordinates.parse(from, "from"), Coordinates.parse(to, "to"),
                TravelMode.valueOf(mode.toUpperCase(Locale.ROOT)),
                Optimization.valueOf(optimize.toUpperCase(Locale.ROOT)), parseDeparture(departAt),
                Boolean.parseBoolean(alternatives));
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
