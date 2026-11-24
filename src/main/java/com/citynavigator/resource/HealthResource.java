package com.citynavigator.resource;

import com.citynavigator.dto.PingResponse;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Liveness check for the hosting platform: 200 as soon as the web layer is deployed, whether or not the routing graph
 * is loaded yet. The graph is prepared in the background (see {@code ApplicationStartup}), and the first start imports
 * the road network into the database, which can take minutes against a remote database. A platform with a short
 * health-check window (such as Render's free plan) would otherwise give up before that finished. Whether routes can
 * actually be served is reported by {@code /api/ping} instead.
 */
@Path("health")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class HealthResource {

    @GET
    public PingResponse health() {
        return new PingResponse("UP");
    }
}
