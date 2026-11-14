package com.citynavigator.resource;

import com.citynavigator.dto.MapInfo;
import com.citynavigator.service.MapService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * {@code /api/map}: the imported area.
 */
@Path("map")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class MapResource {

    private MapService maps;

    protected MapResource() {
        // for the CDI proxy
    }

    @Inject
    public MapResource(MapService maps) {
        this.maps = maps;
    }

    @GET
    public MapInfo info() {
        return maps.info();
    }
}
