package com.citynavigator.resource;

import com.citynavigator.dto.TransitResponse;
import com.citynavigator.service.TransitService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/**
 * {@code /api/transit?from=lat,lon&to=lat,lon&departAt=2026-11-16T08:00+03:00}: a journey by public transport in
 * Istanbul, with its walks and rides. Without {@code departAt} the journey starts now.
 */
@Path("transit")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class TransitResource {

    private TransitService transit;

    protected TransitResource() {
        // for the CDI proxy
    }

    @Inject
    public TransitResource(TransitService transit) {
        this.transit = transit;
    }

    @GET
    public TransitResponse plan(
            @QueryParam("from") @NotNull(message = "gerekli")
            @Pattern(regexp = Coordinates.PATTERN, message = Coordinates.MESSAGE) String from,
            @QueryParam("to") @NotNull(message = "gerekli")
            @Pattern(regexp = Coordinates.PATTERN, message = Coordinates.MESSAGE) String to,
            @QueryParam("departAt")
            @Pattern(regexp = RouteResource.DEPARTURE_PATTERN, message = "saat dilimiyle birlikte ISO-8601 zamanı "
                    + "olmalı, örneğin 2026-11-14T18:00+03:00") String departAt) {
        return transit.plan(Coordinates.parse(from, "from"), Coordinates.parse(to, "to"),
                RouteResource.parseDeparture(departAt));
    }
}
