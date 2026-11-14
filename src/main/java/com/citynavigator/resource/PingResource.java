package com.citynavigator.resource;

import com.citynavigator.dto.PingResponse;
import com.citynavigator.service.RoutingEngine;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Health check for the platform: 200 once the routing graph is in memory, 503 while it is still loading, so the
 * platform sends no traffic to an instance that cannot route yet.
 */
@Path("ping")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class PingResource {

    private RoutingEngine engine;

    protected PingResource() {
        // for the CDI proxy
    }

    @Inject
    public PingResource(RoutingEngine engine) {
        this.engine = engine;
    }

    @GET
    public Response ping() {
        return engine.isLoaded()
                ? Response.ok(new PingResponse("UP")).build()
                : Response.status(Response.Status.SERVICE_UNAVAILABLE).entity(new PingResponse("LOADING")).build();
    }
}
